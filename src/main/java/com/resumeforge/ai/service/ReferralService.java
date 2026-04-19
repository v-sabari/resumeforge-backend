package com.resumeforge.ai.service;

import com.resumeforge.ai.entity.Referral;
import com.resumeforge.ai.entity.ReferralReward;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.repository.ReferralRepository;
import com.resumeforge.ai.repository.ReferralRewardRepository;
import com.resumeforge.ai.repository.UserRepository;
import com.resumeforge.ai.dto.ReferralStatusResponse;
import com.resumeforge.ai.dto.ReferralStatusResponse.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Referral system: code generation, tracking, qualification, anti-abuse, rewards.
 *
 * Reward milestones (referrals = QUALIFIED referrals only):
 *   1  qualified referral  → 3 days premium extension
 *   3  qualified referrals → ATS Pro Scan feature unlock
 *   5  qualified referrals → 30 days premium extension
 *
 * Qualification criteria (all must be met):
 *   1. Referred user has verified their email
 *   2. Referred user has created at least one resume
 *   3. Account must be at least MIN_ACCOUNT_AGE_HOURS old (anti-abuse)
 *
 * Anti-abuse rules enforced:
 *   - Self-referral blocked at registration (code owner cannot use their own code)
 *   - One referral record per referred user (UNIQUE constraint + code check)
 *   - Account age minimum before qualifying (prevents rapid fake-signup cycles)
 *   - Email masking in responses (privacy)
 */
@Service
public class ReferralService {

    private static final Logger log = LoggerFactory.getLogger(ReferralService.class);

    /** Minimum hours a referred account must exist before the referral qualifies. */
    private static final int MIN_ACCOUNT_AGE_HOURS = 1;

    /** Referral code character set — uppercase alphanumeric, no ambiguous chars (0/O/1/I). */
    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 8;

    /** Reward type constants. */
    public static final String REWARD_PREMIUM_3_DAYS  = "PREMIUM_DAYS_3";
    public static final String REWARD_ATS_PRO_UNLOCK  = "ATS_PRO_UNLOCK";
    public static final String REWARD_PREMIUM_30_DAYS = "PREMIUM_DAYS_30";

    private final ReferralRepository referralRepository;
    private final ReferralRewardRepository rewardRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.openrouter.site-url:https://www.resumeforgeai.site}")
    private String siteUrl;

    public ReferralService(ReferralRepository referralRepository,
                           ReferralRewardRepository rewardRepository,
                           UserRepository userRepository) {
        this.referralRepository = referralRepository;
        this.rewardRepository   = rewardRepository;
        this.userRepository     = userRepository;
    }

    // ─────────────────────────────────────────────────────────────────
    // Code generation
    // ─────────────────────────────────────────────────────────────────

    /**
     * Generates a unique referral code for a new user.
     * Retries up to 10 times if a collision occurs (extremely unlikely at scale).
     */
    public String generateUniqueReferralCode() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String code = generateCode();
            if (!userRepository.existsByReferralCode(code)) {
                return code;
            }
        }
        // Fall back to longer code on collision (should never happen in practice)
        return generateCode() + generateCode().substring(0, 4);
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(secureRandom.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────────────
    // Record inbound referral at registration
    // ─────────────────────────────────────────────────────────────────

    /**
     * Called from AuthService.register() when a new user provides a referral code.
     *
     * Silently ignores invalid codes — registration succeeds regardless.
     * Anti-abuse: prevents self-referral (code owner signs up under their own code).
     */
    @Transactional
    public void recordReferralAtRegistration(User newUser, String referralCode) {
        if (referralCode == null || referralCode.isBlank()) return;

        String code = referralCode.trim().toUpperCase();

        // Look up the referrer by code
        User referrer = userRepository.findByReferralCode(code).orElse(null);
        if (referrer == null) {
            log.debug("Referral code not found: {} — ignoring", code);
            return;
        }

        // Anti-abuse: self-referral not allowed
        if (referrer.getId().equals(newUser.getId())) {
            log.warn("Self-referral attempt blocked: userId={}", newUser.getId());
            return;
        }

        // Anti-abuse: referred user already has a referral record
        if (referralRepository.findByReferredUser(newUser).isPresent()) {
            log.warn("Duplicate referral attempt for userId={}", newUser.getId());
            return;
        }

        Referral referral = Referral.builder()
                .referrer(referrer)
                .referredUser(newUser)
                .referralCodeUsed(code)
                .status("PENDING")
                .build();

        referralRepository.save(referral);
        log.info("Referral recorded: referrerId={} referredUserId={} code={}",
                referrer.getId(), newUser.getId(), code);
    }

    // ─────────────────────────────────────────────────────────────────
    // Qualification trigger — called from AuthService and ResumeService
    // ─────────────────────────────────────────────────────────────────

    /**
     * Checks if a referred user now meets all qualification criteria.
     * If they do, marks the referral QUALIFIED and triggers reward evaluation.
     *
     * Called from:
     *   - AuthService.verifyEmailOtp() — after email is verified
     *   - ResumeService.create()       — after first resume is created
     *
     * Idempotent: safe to call multiple times, won't double-qualify.
     */
    @Transactional
    public void checkAndQualifyReferral(User referredUser) {
        Referral referral = referralRepository.findByReferredUser(referredUser).orElse(null);
        if (referral == null || !"PENDING".equals(referral.getStatus())) {
            return; // no referral, or already processed
        }

        // ── Anti-abuse: account age check ────────────────────────────
        Instant minAge = referredUser.getCreatedAt().plus(MIN_ACCOUNT_AGE_HOURS, ChronoUnit.HOURS);
        if (Instant.now().isBefore(minAge)) {
            log.debug("Referral qualification deferred: account too new. userId={}",
                    referredUser.getId());
            return;
        }

        // ── Qualification criteria ────────────────────────────────────
        boolean emailVerified = referredUser.isEmailVerified();
        boolean hasResume     = !referredUser.getResumes().isEmpty();

        if (!emailVerified || !hasResume) {
            log.debug("Referral not yet qualified: userId={} emailVerified={} hasResume={}",
                    referredUser.getId(), emailVerified, hasResume);
            return;
        }

        // ── Qualify ───────────────────────────────────────────────────
        referral.setStatus("QUALIFIED");
        referral.setQualifiedAt(Instant.now());
        referralRepository.save(referral);

        log.info("Referral qualified: referralId={} referrerId={} referredUserId={}",
                referral.getId(), referral.getReferrer().getId(), referredUser.getId());

        // ── Evaluate and grant rewards to the referrer ────────────────
        checkAndGrantRewards(referral.getReferrer(), referral);
    }

    // ─────────────────────────────────────────────────────────────────
    // Reward engine
    // ─────────────────────────────────────────────────────────────────

    /**
     * Evaluates milestone rewards for the referrer after a new referral qualifies.
     *
     * Milestones:
     *   1st qualified → PREMIUM_DAYS_3
     *   3rd qualified → ATS_PRO_UNLOCK
     *   5th qualified → PREMIUM_DAYS_30
     *
     * Each milestone is granted exactly once (idempotency enforced by
     * ReferralRewardRepository.existsByUserAndMilestoneCount).
     */
    @Transactional
    public void checkAndGrantRewards(User referrer, Referral triggeringReferral) {
        long totalQualified = referralRepository.countQualifiedByReferrer(referrer);
        log.info("Checking rewards for referrerId={}: totalQualified={}",
                referrer.getId(), totalQualified);

        if (totalQualified >= 1 && !rewardRepository.existsByUserAndMilestoneCount(referrer, 1)) {
            grantPremiumDays(referrer, triggeringReferral, 3, 1,
                    "3 days Premium — reward for your 1st successful referral");
        }
        if (totalQualified >= 3 && !rewardRepository.existsByUserAndMilestoneCount(referrer, 3)) {
            grantAtsProUnlock(referrer, triggeringReferral);
        }
        if (totalQualified >= 5 && !rewardRepository.existsByUserAndMilestoneCount(referrer, 5)) {
            grantPremiumDays(referrer, triggeringReferral, 30, 5,
                    "30 days Premium — reward for your 5th successful referral");
        }
    }

    private void grantPremiumDays(User referrer, Referral referral,
                                   int days, int milestone, String description) {
        Instant now = Instant.now();

        // Extend premium: if already premium, push expiry forward; if not, start now
        Instant currentExpiry = referrer.getPremiumExpiresAt();
        Instant baseInstant   = (referrer.isPremium() && currentExpiry != null
                                  && currentExpiry.isAfter(now))
                                  ? currentExpiry   // extend from current expiry
                                  : now;            // start fresh
        Instant newExpiry = baseInstant.plus(days, ChronoUnit.DAYS);

        // If user has lifetime premium (premiumExpiresAt=null), don't overwrite it
        // with a time-limited extension — just record the reward for analytics.
        if (referrer.isPremium() && currentExpiry == null) {
            log.info("Referrer userId={} already has lifetime premium — reward logged only",
                    referrer.getId());
        } else {
            referrer.setPremium(true);
            referrer.setPremiumExpiresAt(newExpiry);
            userRepository.save(referrer);
            log.info("Granted {} premium days to userId={}: new expiry={}",
                    days, referrer.getId(), newExpiry);
        }

        ReferralReward reward = ReferralReward.builder()
                .user(referrer)
                .referral(referral)
                .rewardType(days == 3 ? REWARD_PREMIUM_3_DAYS : REWARD_PREMIUM_30_DAYS)
                .rewardDescription(description)
                .milestoneCount(milestone)
                .expiresAt(newExpiry)
                .build();
        rewardRepository.save(reward);
    }

    private void grantAtsProUnlock(User referrer, Referral referral) {
        // ATS Pro Unlock is a permanent feature flag — stored as a reward record.
        // The AiService will check for this reward type before gating ATS score calls.
        ReferralReward reward = ReferralReward.builder()
                .user(referrer)
                .referral(referral)
                .rewardType(REWARD_ATS_PRO_UNLOCK)
                .rewardDescription("ATS Pro Scan — unlimited scans unlocked for your 3rd referral")
                .milestoneCount(3)
                .build();
        rewardRepository.save(reward);

        log.info("Granted ATS Pro Unlock to referrerId={}", referrer.getId());
    }

    // ─────────────────────────────────────────────────────────────────
    // Status for the authenticated user
    // ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ReferralStatusResponse getStatus(User user) {
        String code = user.getReferralCode();
        String link = buildReferralLink(code);

        List<Referral> referrals = referralRepository.findByReferrerOrderByCreatedAtDesc(user);
        long qualified = referrals.stream().filter(r -> "QUALIFIED".equals(r.getStatus())).count();
        long pending   = referrals.stream().filter(r -> "PENDING".equals(r.getStatus())).count();

        List<ReferralHistoryItem> history = referrals.stream()
                .map(r -> new ReferralHistoryItem(
                        r.getId(),
                        maskEmail(r.getReferredUser().getEmail()),
                        r.getStatus(),
                        r.getCreatedAt(),
                        r.getQualifiedAt()
                ))
                .toList();

        List<RewardItem> rewards = rewardRepository.findByUserOrderByGrantedAtDesc(user).stream()
                .map(r -> new RewardItem(
                        r.getRewardType(),
                        r.getRewardDescription(),
                        r.getMilestoneCount(),
                        r.getGrantedAt(),
                        r.getExpiresAt()
                ))
                .toList();

        NextMilestone next = computeNextMilestone(qualified);

        return new ReferralStatusResponse(code, link, qualified, pending, history, rewards, next);
    }

    private NextMilestone computeNextMilestone(long qualified) {
        if (qualified < 1) return new NextMilestone(1, (int)(1 - qualified), "3 days Premium");
        if (qualified < 3) return new NextMilestone(3, (int)(3 - qualified), "ATS Pro Scan (unlimited)");
        if (qualified < 5) return new NextMilestone(5, (int)(5 - qualified), "30 days Premium");
        return new NextMilestone(5, 0, "All milestones reached!");
    }

    private String buildReferralLink(String code) {
        if (code == null) return "";
        String base = siteUrl.endsWith("/") ? siteUrl.substring(0, siteUrl.length() - 1) : siteUrl;
        // Use the non-www root for sharing
        base = base.replace("www.", "");
        return base + "/register?ref=" + code;
    }

    /** Masks an email for display: john.doe@gmail.com → j***@gmail.com */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "****";
        String[] parts = email.split("@", 2);
        String local  = parts[0];
        String domain = parts[1];
        return (local.length() > 1 ? local.charAt(0) + "***" : "***") + "@" + domain;
    }
}
