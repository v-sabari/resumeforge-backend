package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.ReferralHistory;
import com.resumeforge.ai.entity.ReferralReward;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.ApiException;
import com.resumeforge.ai.repository.ReferralHistoryRepository;
import com.resumeforge.ai.repository.ReferralRewardRepository;
import com.resumeforge.ai.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReferralService {
    private static final List<Milestone> MILESTONES = List.of(
            new Milestone(1, ReferralReward.RewardType.PREMIUM_DAYS_3, "3 days Premium", Duration.ofDays(3)),
            new Milestone(3, ReferralReward.RewardType.ATS_PRO_UNLOCK, "ATS Pro Scan (unlimited)", null),
            new Milestone(5, ReferralReward.RewardType.PREMIUM_DAYS_30, "30 days Premium", Duration.ofDays(30))
    );

    private final UserRepository userRepository;
    private final ReferralHistoryRepository referralHistoryRepository;
    private final ReferralRewardRepository referralRewardRepository;
    private final String publicAppBaseUrl;

    public ReferralService(
            UserRepository userRepository,
            ReferralHistoryRepository referralHistoryRepository,
            ReferralRewardRepository referralRewardRepository,
            @Value("${app.public-base-url:https://resumeforgeai.site}") String publicAppBaseUrl
    ) {
        this.userRepository = userRepository;
        this.referralHistoryRepository = referralHistoryRepository;
        this.referralRewardRepository = referralRewardRepository;
        this.publicAppBaseUrl = trimTrailingSlash(publicAppBaseUrl);
    }

    @Transactional
    public void attachReferralAtSignup(User newUser, String rawReferralCode) {
        ensureReferralCode(newUser);
        userRepository.save(newUser);

        if (rawReferralCode == null || rawReferralCode.isBlank()) {
            return;
        }

        String referralCode = rawReferralCode.trim().toUpperCase(Locale.ROOT);
        User referrer = userRepository.findByReferralCode(referralCode)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Invalid referral code"));

        if (referrer.getId().equals(newUser.getId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot refer yourself");
        }

        referralHistoryRepository.findByReferredUser(newUser).ifPresent(existing -> {
            throw new ApiException(HttpStatus.CONFLICT, "This account already has a referral record");
        });

        ReferralHistory history = ReferralHistory.builder()
                .referrerUser(referrer)
                .referredUser(newUser)
                .status(ReferralHistory.ReferralStatus.PENDING)
                .build();
        referralHistoryRepository.save(history);
    }

    @Transactional
    public void onUserEmailVerified(User user) {
        ReferralHistory history = referralHistoryRepository.findByReferredUser(user).orElse(null);
        if (history == null) {
            ensureReferralCode(user);
            userRepository.save(user);
            return;
        }

        history.setEmailVerified(true);
        reevaluateQualification(history);
    }

    @Transactional
    public void onFirstResumeCreated(User user) {
        boolean changed = !user.isHasCreatedResume();
        user.setHasCreatedResume(true);
        ensureReferralCode(user);
        userRepository.save(user);

        if (!changed) {
            return;
        }

        ReferralHistory history = referralHistoryRepository.findByReferredUser(user).orElse(null);
        if (history == null) {
            return;
        }

        history.setResumeCreated(true);
        reevaluateQualification(history);
    }

    @Transactional
    public ReferralStatusResponse getReferralStatus(User user) {
        String referralCode = ensureReferralCode(user);
        userRepository.save(user);
        List<ReferralHistory> history = referralHistoryRepository.findByReferrerUserOrderByCreatedAtDesc(user);
        List<ReferralReward> rewards = referralRewardRepository.findByUserOrderByGrantedAtDesc(user);

        long qualified = history.stream().filter(h -> h.getStatus() == ReferralHistory.ReferralStatus.QUALIFIED).count();
        long pending = history.stream().filter(h -> h.getStatus() == ReferralHistory.ReferralStatus.PENDING).count();

        return new ReferralStatusResponse(
                referralCode,
                publicAppBaseUrl + "/register?ref=" + referralCode,
                qualified,
                pending,
                history.stream().map(this::toHistoryResponse).toList(),
                rewards.stream().map(this::toRewardResponse).toList(),
                buildNextMilestone((int) qualified)
        );
    }

    @Transactional
    public String ensureReferralCode(User user) {
        if (user.getReferralCode() != null && !user.getReferralCode().isBlank()) {
            return user.getReferralCode();
        }

        String code;
        do {
            code = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
        } while (userRepository.findByReferralCode(code).isPresent());

        user.setReferralCode(code);
        return code;
    }


    private void reevaluateQualification(ReferralHistory history) {
        boolean qualifies = history.isEmailVerified() && history.isResumeCreated();
        if (qualifies && history.getStatus() != ReferralHistory.ReferralStatus.QUALIFIED) {
            history.setStatus(ReferralHistory.ReferralStatus.QUALIFIED);
            history.setQualifiedAt(Instant.now());
            referralHistoryRepository.save(history);
            grantMilestoneRewardsIfNeeded(history.getReferrerUser());
            return;
        }

        if (!qualifies && history.getStatus() != ReferralHistory.ReferralStatus.PENDING) {
            history.setStatus(ReferralHistory.ReferralStatus.PENDING);
            history.setQualifiedAt(null);
        }
        referralHistoryRepository.save(history);
    }

    private void grantMilestoneRewardsIfNeeded(User referrer) {
        long qualifiedReferrals = referralHistoryRepository.countByReferrerUserAndStatus(referrer, ReferralHistory.ReferralStatus.QUALIFIED);
        for (Milestone milestone : MILESTONES) {
            if (qualifiedReferrals < milestone.count()) {
                continue;
            }
            if (referralRewardRepository.findByUserAndMilestoneCount(referrer, milestone.count()).isPresent()) {
                continue;
            }
            grantReward(referrer, milestone);
        }
    }

    private void grantReward(User referrer, Milestone milestone) {
        Instant now = Instant.now();
        Instant expiresAt = milestone.duration() == null ? null : now.plus(milestone.duration());

        ReferralReward reward = ReferralReward.builder()
                .user(referrer)
                .rewardType(milestone.rewardType())
                .description(milestone.description())
                .milestoneCount(milestone.count())
                .grantedAt(now)
                .expiresAt(expiresAt)
                .build();
        referralRewardRepository.save(reward);

        if (milestone.rewardType() == ReferralReward.RewardType.PREMIUM_DAYS_3 ||
                milestone.rewardType() == ReferralReward.RewardType.PREMIUM_DAYS_30) {
            referrer.setPremium(true);
            Instant currentExpiry = referrer.getPremiumExpiresAt();
            Instant base = currentExpiry != null && currentExpiry.isAfter(now) ? currentExpiry : now;
            referrer.setPremiumExpiresAt(base.plus(milestone.duration()));
            userRepository.save(referrer);
        }
    }

    private ReferralHistoryResponse toHistoryResponse(ReferralHistory history) {
        return new ReferralHistoryResponse(
                history.getId(),
                maskEmail(history.getReferredUser().getEmail()),
                history.getStatus().name(),
                history.getCreatedAt(),
                history.getQualifiedAt()
        );
    }

    private ReferralRewardResponse toRewardResponse(ReferralReward reward) {
        return new ReferralRewardResponse(
                reward.getRewardType().name(),
                reward.getDescription(),
                reward.getMilestoneCount(),
                reward.getGrantedAt(),
                reward.getExpiresAt()
        );
    }

    private NextMilestoneResponse buildNextMilestone(int qualified) {
        Optional<Milestone> next = MILESTONES.stream()
                .sorted(Comparator.comparingInt(Milestone::count))
                .filter(m -> qualified < m.count())
                .findFirst();
        return next.map(m -> new NextMilestoneResponse(m.count(), m.count() - qualified, m.description())).orElse(null);
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "hidden";
        }
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String masked = local.charAt(0) + "***" + local.charAt(local.length() - 1);
        return masked + domain;
    }

    private String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "https://resumeforgeai.site";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private record Milestone(int count, ReferralReward.RewardType rewardType, String description, Duration duration) {}
}
