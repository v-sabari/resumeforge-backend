package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.Payment;
import com.resumeforge.ai.entity.Referral;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.ApiException;
import com.resumeforge.ai.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AdminService {

    private final UserRepository       userRepository;
    private final PaymentRepository    paymentRepository;
    private final AiUsageLogRepository aiUsageLogRepository;
    private final ReferralRepository   referralRepository;
    private final ResumeRepository     resumeRepository;

    public AdminService(UserRepository userRepository,
                        PaymentRepository paymentRepository,
                        AiUsageLogRepository aiUsageLogRepository,
                        ReferralRepository referralRepository,
                        ResumeRepository resumeRepository) {
        this.userRepository      = userRepository;
        this.paymentRepository   = paymentRepository;
        this.aiUsageLogRepository = aiUsageLogRepository;
        this.referralRepository  = referralRepository;
        this.resumeRepository    = resumeRepository;
    }

    // ─────────────────────────────────────────────────────────────────
    // Dashboard stats
    // ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminStatsDto getDashboardStats() {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        return new AdminStatsDto(
                userRepository.count(),
                userRepository.countByCreatedAtAfter(thirtyDaysAgo),
                userRepository.countByPremiumTrue(),
                userRepository.countByEnabledTrue(),
                paymentRepository.totalRevenue(),
                paymentRepository.revenuesSince(thirtyDaysAgo),
                paymentRepository.countByStatus("PAID"),
                paymentRepository.countPaidSince(thirtyDaysAgo),
                aiUsageLogRepository.count(),
                aiUsageLogRepository.countByCreatedAtAfter(thirtyDaysAgo),
                aiUsageLogRepository.totalTokensSince(thirtyDaysAgo),
                referralRepository.countByStatus("QUALIFIED"),
                referralRepository.countQualifiedSince(thirtyDaysAgo),
                referralRepository.countByStatus("PENDING")
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // User management
    // ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AdminUserDto> getUsers(int page, int size, String search) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        Page<User> users = (search != null && !search.isBlank())
                ? userRepository.searchUsers(search.trim(), pageable)
                : userRepository.findAllByOrderByCreatedAtDesc(pageable);

        return users.map(this::toAdminUserDto);
    }

    @Transactional(readOnly = true)
    public AdminUserDto getUserById(Long id) {
        User user = findUser(id);
        return toAdminUserDto(user);
    }

    /** Grant or revoke premium for a user. */
    @Transactional
    public AdminUserDto togglePremium(Long userId, boolean premium) {
        User user = findUser(userId);
        user.setPremium(premium);
        if (!premium) {
            user.setPremiumExpiresAt(null);    // clear any time-limited expiry
        }
        userRepository.save(user);
        return toAdminUserDto(user);
    }

    /** Promote a user to admin or demote to normal user. */
    @Transactional
    public AdminUserDto setRole(Long userId, String role) {
        if (!"USER".equals(role) && !"ADMIN".equals(role)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Role must be 'USER' or 'ADMIN'.");
        }
        User user = findUser(userId);
        user.setRole(role);
        userRepository.save(user);
        return toAdminUserDto(user);
    }

    // ─────────────────────────────────────────────────────────────────
    // Payment history
    // ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AdminPaymentDto> getPayments(int page, int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        return paymentRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toAdminPaymentDto);
    }

    // ─────────────────────────────────────────────────────────────────
    // AI usage analytics
    // ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminAiStatsDto getAiStats() {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        List<AdminAiStatsDto.FeatureCount> breakdown =
                aiUsageLogRepository.featureBreakdownSince(thirtyDaysAgo)
                        .stream()
                        .map(r -> new AdminAiStatsDto.FeatureCount(r.getFeature(), r.getCallCount()))
                        .toList();

        return new AdminAiStatsDto(
                aiUsageLogRepository.countByCreatedAtAfter(thirtyDaysAgo),
                aiUsageLogRepository.totalTokensSince(thirtyDaysAgo),
                breakdown
        );
    }

    // ─────────────────────────────────────────────────────────────────
    // Referral analytics
    // ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public AdminReferralStatsDto getReferralStats() {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);

        long total     = referralRepository.count();
        long qualified = referralRepository.countByStatus("QUALIFIED");
        long pending   = referralRepository.countByStatus("PENDING");
        long qualRecent = referralRepository.countQualifiedSince(thirtyDaysAgo);
        double rate    = total > 0 ? (qualified * 100.0 / total) : 0.0;

        List<AdminReferralStatsDto.TopReferrer> top =
                referralRepository.topReferrers(PageRequest.of(0, 10))
                        .stream()
                        .map(r -> new AdminReferralStatsDto.TopReferrer(
                                r.getReferrer().getId(),
                                r.getReferrer().getName(),
                                r.getReferrer().getEmail(),
                                r.getQualifiedCount()
                        ))
                        .toList();

        return new AdminReferralStatsDto(total, qualified, pending, qualRecent, rate, top);
    }

    // ─────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────

    private User findUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
    }

    private AdminUserDto toAdminUserDto(User u) {
        int resumeCount = (int) resumeRepository.countByUser(u);
        return new AdminUserDto(
                u.getId(), u.getName(), u.getEmail(),
                u.isPremium(), u.getPremiumExpiresAt(),
                u.isEmailVerified(), u.getRole(), u.getReferralCode(),
                resumeCount, u.getCreatedAt()
        );
    }

    private AdminPaymentDto toAdminPaymentDto(Payment p) {
        return new AdminPaymentDto(
                p.getId(),
                p.getPaymentId(),
                p.getRazorpayPaymentId(),
                p.getUser().getId(),
                p.getUser().getEmail(),
                p.getAmount(),
                p.getStatus(),
                p.getCreatedAt(),
                p.getCapturedAt()
        );
    }
}
