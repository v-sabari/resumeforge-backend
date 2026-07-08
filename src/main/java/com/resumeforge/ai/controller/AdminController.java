package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.service.AdminService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    @Autowired
    private AdminService adminService;

    @GetMapping("/stats")
    public ResponseEntity<AdminStatsResponse> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    @GetMapping("/users")
    public ResponseEntity<Page<AdminUserResponse>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String q) {
        return ResponseEntity.ok(adminService.getUsers(page, size, q));
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<AdminUserResponse> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getUserById(id));
    }

    @PostMapping("/users/{id}/role")
    public ResponseEntity<ApiResponse> setUserRole(
            @PathVariable Long id,
            @Valid @RequestBody SetRoleRequest request) {
        return ResponseEntity.ok(adminService.setUserRole(id, request));
    }

    @PostMapping("/users/{id}/toggle-premium")
    public ResponseEntity<ApiResponse> togglePremium(
            @PathVariable Long id,
            @Valid @RequestBody TogglePremiumRequest request) {
        return ResponseEntity.ok(adminService.togglePremium(id, request));
    }

    @GetMapping("/payments")
    public ResponseEntity<Page<PaymentResponse>> getPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.getPayments(page, size));
    }

    @GetMapping("/ai-stats")
    public ResponseEntity<Map<String, Object>> getAiStats() {
        return ResponseEntity.ok(adminService.getAiStats());
    }

    @GetMapping("/referral-stats")
    public ResponseEntity<Map<String, Object>> getReferralStats() {
        return ResponseEntity.ok(adminService.getReferralStats());
    }
}
