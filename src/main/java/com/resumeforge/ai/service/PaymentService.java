package com.resumeforge.ai.service;

import com.resumeforge.ai.config.RazorpayProperties;
import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.Payment;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.ApiException;
import com.resumeforge.ai.repository.PaymentRepository;
import com.resumeforge.ai.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final RazorpayProperties razorpayProperties;

    public PaymentService(PaymentRepository paymentRepository, UserRepository userRepository, RazorpayProperties razorpayProperties) {
        this.paymentRepository = paymentRepository;
        this.userRepository = userRepository;
        this.razorpayProperties = razorpayProperties;
    }

    @Transactional
    public PaymentCreateResponse create(User user, PaymentCreateRequest request) {
        BigDecimal amount = request != null && request.amount() != null ? request.amount() : new BigDecimal("99.00");
        String paymentId = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        Payment payment = Payment.builder()
                .user(user)
                .paymentId(paymentId)
                .amount(amount)
                .status("CREATED")
                .build();
        paymentRepository.save(payment);
        return new PaymentCreateResponse(
                paymentId,
                amount,
                payment.getStatus(),
                razorpayProperties.paymentLink(),
                razorpayProperties.keyId(),
                "Payment intent created. Redirect the user to Razorpay to complete premium upgrade."
        );
    }

    @Transactional
    public PaymentResponse verify(User user, PaymentVerifyRequest request) {
        if (request == null || request.paymentId() == null || request.paymentId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "paymentId is required");
        }
        Payment payment = paymentRepository.findByPaymentIdAndUser(request.paymentId(), user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment record not found"));

        String status = (request.status() == null || request.status().isBlank()) ? "VERIFIED" : request.status().trim().toUpperCase();
        if (!List.of("CREATED", "PENDING", "PAID", "VERIFIED", "FAILED").contains(status)) {
            status = "VERIFIED";
        }
        payment.setStatus(status);
        paymentRepository.save(payment);

        if (status.equals("PAID") || status.equals("VERIFIED")) {
            user.setPremium(true);
            userRepository.save(user);
        }
        return toResponse(payment);
    }

    public List<PaymentResponse> history(User user) {
        return paymentRepository.findByUserOrderByCreatedAtDesc(user).stream().map(this::toResponse).toList();
    }

    private PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getPaymentId(), payment.getAmount(), payment.getStatus(), payment.getCreatedAt());
    }
}
