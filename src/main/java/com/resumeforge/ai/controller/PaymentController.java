package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/create")
    public ResponseEntity<Map<String, Object>> createPayment(
            @AuthenticationPrincipal User user,
            @RequestBody(required = false) PaymentCreateRequest request) {
        if (request == null) {
            request = new PaymentCreateRequest();
        }
        return ResponseEntity.ok(paymentService.createPaymentOrder(user, request));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse> verifyPayment(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody PaymentVerifyRequest request) {
        return ResponseEntity.ok(paymentService.verifyPayment(user, request));
    }

    @GetMapping("/history")
    public ResponseEntity<List<PaymentResponse>> getHistory(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(paymentService.getPaymentHistory(user));
    }
}
