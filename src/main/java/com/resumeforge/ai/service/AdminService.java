package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.ResourceNotFoundException;
import com.resumeforge.ai.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        long totalUsers = userRepository.count();
        long premiumUsers = userRepository.countByPremiumTrue();
        long verifiedUsers = userRepository.countByEmailVerifiedTrue();
        long totalResumes = resumeRepository.count();
        long totalPayments = paymentRepository.count();
        long pendingPayments = paymentRepository.countByStatus("PENDING");
        long completedPayments = paymentRepository.countByStatus("COMPLETED");

        return AdminStatsResponse.builder()
                .totalUsers(totalUsers)
                .premiumUsers(premiumUsers)
                .verifiedUsers(verifiedUsers)
                .totalResumes(totalResumes)
                .totalPayments(totalPayments)
                .pendingPayments(pendingPayments)
                .completedPayments(completedPayments)
                .build();
    }

    public Page<AdminUserResponse> getUsers(int page, int size, String query) {
        Page<User> users;
        if (query != null && !query.isEmpty()) {
            users = userRepository.searchUsers(query, PageRequest.of(page, size));
        } else {
            users = userRepository.findAll(PageRequest.of(page, size));
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
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setRole(request.getRole());
        userRepository.save(user);

        return ApiResponse.success("User role updated");
    }

    @Transactional
    public ApiResponse togglePremium(Long userId, TogglePremiumRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setPremium(request.getPremium());
        userRepository.save(user);

        return ApiResponse.success("Premium status updated");
    }

    public Page<PaymentResponse> getPayments(int page, int size) {
        return paymentService.getAllPayments(page, size);
    }

    public Map<String, Object> getAiStats() {
        List<Object[]> featureStats = aiUsageLogRepository.getFeatureUsageStats();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsage", aiUsageLogRepository.count());
        stats.put("featureBreakdown", featureStats.stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                )));

        return stats;
    }

    public Map<String, Object> getReferralStats() {
        long totalRewards = referralRewardRepository.count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRewards", totalRewards);
        stats.put("pendingRewards", 0);

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