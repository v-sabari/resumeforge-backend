package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.service.CurrentUserService;
import com.resumeforge.ai.service.PaymentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentsController {
    private final PaymentService paymentService;
    private final CurrentUserService currentUserService;

    public PaymentsController(PaymentService paymentService, CurrentUserService currentUserService) {
        this.paymentService = paymentService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/create")
    public PaymentCreateResponse create(@RequestBody(required = false) PaymentCreateRequest request) {
        return paymentService.create(currentUserService.getCurrentUser(), request);
    }

    @PostMapping("/verify")
    public PaymentResponse verify(@RequestBody PaymentVerifyRequest request) {
        return paymentService.verify(currentUserService.getCurrentUser(), request);
    }

    @GetMapping("/history")
    public List<PaymentResponse> history() {
        return paymentService.history(currentUserService.getCurrentUser());
    }
}
