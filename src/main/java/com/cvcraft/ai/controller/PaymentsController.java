package com.cvcraft.ai.controller;
import com.cvcraft.ai.dto.*;
import com.cvcraft.ai.service.*;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/payments")
public class PaymentsController {
    private final PaymentService paymentService;
    private final CurrentUserService currentUserService;
    public PaymentsController(PaymentService p, CurrentUserService c) { this.paymentService = p; this.currentUserService = c; }
    @PostMapping("/create") public PaymentCreateResponse create(@RequestBody(required = false) PaymentCreateRequest req) { return paymentService.create(currentUserService.getCurrentUser(), req); }
    @PostMapping("/verify") public PaymentResponse verify(@RequestBody PaymentVerifyRequest req) { return paymentService.verify(currentUserService.getCurrentUser(), req); }
    @GetMapping("/history")  public List<PaymentResponse> history() { return paymentService.history(currentUserService.getCurrentUser()); }
}
