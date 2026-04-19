package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.service.AdminService;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only REST endpoints.
 *
 * All routes under /api/admin/** are protected by:
 *   1. JWT authentication (via JwtAuthenticationFilter)
 *   2. ROLE_ADMIN authority check (in SecurityConfig: .hasRole("ADMIN"))
 *
 * Any non-admin JWT receives HTTP 403. Any unauthenticated request receives HTTP 401.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // ── Dashboard ─────────────────────────────────────────────────────

    /**
     * GET /api/admin/stats
     * Returns all headline stats for the admin dashboard:
     *   users, revenue, AI calls, referrals — all-time and last 30 days.
     */
    @GetMapping("/stats")
    public AdminStatsDto stats() {
        return adminService.getDashboardStats();
    }

    // ── Users ─────────────────────────────────────────────────────────

    /**
     * GET /api/admin/users?page=0&size=20&q=searchTerm
     * Paginated user list, newest first. Optionally filtered by name/email.
     */
    @GetMapping("/users")
    public Page<AdminUserDto> users(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String q
    ) {
        return adminService.getUsers(page, size, q);
    }

    /**
     * GET /api/admin/users/{id}
     * Single user detail with resume count.
     */
    @GetMapping("/users/{id}")
    public AdminUserDto user(@PathVariable Long id) {
        return adminService.getUserById(id);
    }

    /**
     * POST /api/admin/users/{id}/toggle-premium
     * Body: { "premium": true | false }
     * Grants or revokes lifetime premium. Clears premiumExpiresAt when revoking.
     */
    @PostMapping("/users/{id}/toggle-premium")
    public AdminUserDto togglePremium(@PathVariable Long id,
                                       @RequestBody AdminTogglePremiumRequest request) {
        return adminService.togglePremium(id, request.isPremium());
    }

    /**
     * POST /api/admin/users/{id}/role
     * Body: { "role": "ADMIN" | "USER" }
     * Promotes or demotes a user's role.
     */
    @PostMapping("/users/{id}/role")
    public AdminUserDto setRole(@PathVariable Long id,
                                 @RequestBody AdminSetRoleRequest request) {
        return adminService.setRole(id, request.getRole());
    }

    // ── Payments ──────────────────────────────────────────────────────

    /**
     * GET /api/admin/payments?page=0&size=20
     * Paginated payment history across all users, newest first.
     */
    @GetMapping("/payments")
    public Page<AdminPaymentDto> payments(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return adminService.getPayments(page, size);
    }

    // ── AI analytics ──────────────────────────────────────────────────

    /**
     * GET /api/admin/ai-stats
     * AI feature usage breakdown for the last 30 days:
     *   total calls, total tokens used, per-feature breakdown.
     */
    @GetMapping("/ai-stats")
    public AdminAiStatsDto aiStats() {
        return adminService.getAiStats();
    }

    // ── Referral analytics ────────────────────────────────────────────

    /**
     * GET /api/admin/referral-stats
     * Referral system analytics:
     *   total/qualified/pending counts, conversion rate, top referrers.
     */
    @GetMapping("/referral-stats")
    public AdminReferralStatsDto referralStats() {
        return adminService.getReferralStats();
    }
}
