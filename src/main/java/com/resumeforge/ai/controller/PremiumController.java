package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.ApiResponse;
import com.resumeforge.ai.dto.PremiumActivateRequest;
import com.resumeforge.ai.dto.PremiumStatusResponse;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/premium")
public class PremiumController {

    @Autowired
    private PaymentService paymentService;

    @GetMapping("/status")
    public ResponseEntity<PremiumStatusResponse> getStatus(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(paymentService.getPremiumStatus(user));
    }

    @PostMapping("/activate")
    public ResponseEntity<ApiResponse> activate(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PremiumActivateRequest request) {
        return ResponseEntity.ok(paymentService.activatePremium(user, request));
    }
}
