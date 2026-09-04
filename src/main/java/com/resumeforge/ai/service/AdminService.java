package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.BadRequestException;
import com.resumeforge.ai.exception.ResourceNotFoundException;
import com.resumeforge.ai.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private AiUsageLogRepository aiUsageLogRepository;

    @Autowired
    private ReferralRewardRepository referralRewardRepository;

    @Autowired
    private PaymentService paymentService;



    public AdminStatsResponse getStats() {
        Instant thirtyDaysAgoInstant = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        long totalUsers = userRepository.count();
        long premiumUsers = userRepository.countByPremiumTrue();
        long verifiedUsers = userRepository.countByEmailVerifiedTrue();
        long totalResumes = resumeRepository.count();
        long newUsersLast30Days = userRepository.countByCreatedAtAfter(thirtyDaysAgoInstant);

        long totalPayments = paymentRepository.count();
        long pendingPayments = paymentRepository.countByStatus("PENDING");
        long completedPayments = paymentRepository.countByStatus("COMPLETED");
        BigDecimal totalRevenue = paymentRepository.sumCompletedAmount();
        BigDecimal revenueLast30Days = paymentRepository.sumCompletedAmountAfter(thirtyDaysAgo);
        long paidLast30Days = paymentRepository.countByStatusAndCreatedAtAfter("COMPLETED", thirtyDaysAgo);

        long totalAiCalls = aiUsageLogRepository.count();
        long aiCallsLast30Days = aiUsageLogRepository.countByCreatedAtAfter(thirtyDaysAgo);
        long tokensLast30Days = aiUsageLogRepository.sumTokensAfter(thirtyDaysAgo);

        long qualifiedReferrals = userRepository.countByReferredByUserIdIsNotNullAndEmailVerifiedTrue();
        long pendingReferrals = userRepository.countByReferredByUserIdIsNotNullAndEmailVerifiedFalse();
        long qualifiedLast30Days = userRepository.countByReferredByUserIdIsNotNullAndEmailVerifiedTrueAndCreatedAtAfter(thirtyDaysAgoInstant);

        return AdminStatsResponse.builder()
                .totalUsers(totalUsers)
                .newUsersLast30Days(newUsersLast30Days)
                .premiumUsers(premiumUsers)
                .verifiedUsers(verifiedUsers)
                .totalResumes(totalResumes)
                .totalPayments(totalPayments)
                .pendingPayments(pendingPayments)
                .completedPayments(completedPayments)
                .totalRevenue(totalRevenue)
                .revenueLast30Days(revenueLast30Days)
                .totalPaidPayments(completedPayments)
                .paidPaymentsLast30Days(paidLast30Days)
                .totalAiCalls(totalAiCalls)
                .aiCallsLast30Days(aiCallsLast30Days)
                .totalTokensLast30Days(tokensLast30Days)
                .totalQualifiedReferrals(qualifiedReferrals)
                .qualifiedReferralsLast30Days(qualifiedLast30Days)
                .pendingReferrals(pendingReferrals)
                .build();
    }

    public Page<AdminUserResponse> getUsers(int page, int size, String query) {
        // SEC FIX: clamp pagination so a hostile admin-facing request cannot
        // trigger an unbounded query / huge page allocation.
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Page<User> users;
        if (query != null && !query.isEmpty()) {
            users = userRepository.searchUsers(query, PageRequest.of(safePage, safeSize));
        } else {
            users = userRepository.findAll(PageRequest.of(safePage, safeSize));
        }
        return users.map(this::toAdminUserResponse);
    }

    public AdminUserResponse getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toAdminUserResponse(user);
    }

    @Transactional
    public ApiResponse setUserRole(Long userId, SetRoleRequest request) {
        // SEC FIX: only accept known roles — an unbounded value would otherwise
        // create e.g. ROLE_"SUPERADMIN" or garbage authority strings.
        String role = request.getRole();
        if (role == null || !(role.equals("USER") || role.equals("ADMIN"))) {
            throw new BadRequestException("Role must be either USER or ADMIN");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setRole(role);
        userRepository.save(user);

        return ApiResponse.success("User role updated");
    }

    @Transactional
    public AdminUserResponse togglePremium(Long userId, TogglePremiumRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Since the user's premium status is changing, re-fetch the managed
        // entity so the returned AdminUserResponse reflects the persisted state.
        User managed = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // SEC/BUS FIX: an admin grant is permanent — clear any time-limited
        // referral expiry so isPremium() (which honors premiumExpiresAt) does not
        // deactivate the admin grant later. Without this, granting premium to a
        // user with an expired referral expiry would have no lasting effect.
        managed.setPremium(request.getPremium());
        managed.setPremiumExpiresAt(null);
        managed = userRepository.save(managed);

        // Return the updated user (AdminUserResponse) so the admin UI has a
        // single authoritative source to reflect the new premium status, instead
        // of a bare success envelope that carries no user data.
        return toAdminUserResponse(managed);
    }

    public Page<PaymentResponse> getPayments(int page, int size) {
        // SEC FIX: clamp pagination (same reasoning as getUsers).
        return paymentService.getAllPayments(Math.max(0, page), Math.min(Math.max(1, size), 100));
    }

    public Map<String, Object> getAiStats() {
        List<Object[]> featureStats = aiUsageLogRepository.getFeatureUsageStats();

        List<Map<String, Object>> featureBreakdown = featureStats.stream()
                .map(row -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("feature", (String) row[0]);
                    item.put("count", (Long) row[1]);
                    return item;
                })
                .collect(Collectors.toList());

        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsage", aiUsageLogRepository.count());
        stats.put("totalCallsLast30Days", aiUsageLogRepository.countByCreatedAtAfter(thirtyDaysAgo));
        stats.put("totalTokensLast30Days", aiUsageLogRepository.sumTokensAfter(thirtyDaysAgo));
        stats.put("featureBreakdown", featureBreakdown);

        return stats;
    }

    public Map<String, Object> getReferralStats() {
        Instant thirtyDaysAgo = Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);

        long totalReferrals = userRepository.countByReferredByUserIdIsNotNull();
        long qualifiedReferrals = userRepository.countByReferredByUserIdIsNotNullAndEmailVerifiedTrue();
        long pendingReferrals = userRepository.countByReferredByUserIdIsNotNullAndEmailVerifiedFalse();
        long qualifiedLast30Days = userRepository.countByReferredByUserIdIsNotNullAndEmailVerifiedTrueAndCreatedAtAfter(thirtyDaysAgo);
        double conversionRate = totalReferrals == 0 ? 0.0 : (qualifiedReferrals * 100.0) / totalReferrals;

        List<Object[]> topRows = userRepository.getTopReferrerCounts(PageRequest.of(0, 5));
        List<Map<String, Object>> topReferrers = topRows.stream().map(row -> {
            Long referrerId = (Long) row[0];
            Long qualifiedCount = (Long) row[1];
            User referrer = userRepository.findById(referrerId).orElse(null);

            Map<String, Object> entry = new HashMap<>();
            entry.put("userId", referrerId);
            entry.put("userName", referrer != null ? referrer.getName() : "Unknown");
            entry.put("userEmail", referrer != null ? referrer.getEmail() : "");
            entry.put("qualifiedCount", qualifiedCount);
            return entry;
        }).collect(Collectors.toList());

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalReferrals", totalReferrals);
        stats.put("qualifiedReferrals", qualifiedReferrals);
        stats.put("pendingReferrals", pendingReferrals);
        stats.put("conversionRate", conversionRate);
        stats.put("qualifiedLast30Days", qualifiedLast30Days);
        stats.put("topReferrers", topReferrers);

        return stats;
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .premium(user.isPremium())
                .emailVerified(user.isEmailVerified())
                .referralCode(user.getReferralCode())
                .referredByUserId(user.getReferredByUserId())
                .createdAt(
                        user.getCreatedAt() != null
                                ? user.getCreatedAt().atZone(ZoneId.systemDefault()).toLocalDateTime()
                                : null
                )
                .build();
    }
}