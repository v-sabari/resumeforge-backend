package com.resumeforge.ai.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.Payment;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.BadRequestException;
import com.resumeforge.ai.exception.ResourceNotFoundException;
import com.resumeforge.ai.repository.PaymentRepository;
import com.resumeforge.ai.repository.UserRepository;
import org.apache.commons.codec.digest.HmacUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @Value("${app.razorpay.key-id}")
    private String razorpayKeyId;

    @Value("${app.razorpay.key-secret}")
    private String razorpayKeySecret;

    @Value("${app.razorpay.webhook-secret}")
    private String webhookSecret;

    @Transactional
    public Map<String, Object> createPaymentOrder(User user, PaymentCreateRequest request) {
        try {
            RazorpayClient client = new RazorpayClient(razorpayKeyId, razorpayKeySecret);

            BigDecimal amount = request.getAmount() != null
                    ? request.getAmount()
                    : new BigDecimal("99.00");

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amount.multiply(new BigDecimal("100")).intValue());
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "rcpt_" + System.currentTimeMillis());
            orderRequest.put("payment_capture", 1);

            Order order = client.orders.create(orderRequest);
            String razorpayOrderId = order.get("id");

            Payment payment = Payment.builder()
                    .userId(user.getId())
                    .razorpayOrderId(razorpayOrderId)
                    .amount(amount)
                    .currency("INR")
                    .status("PENDING")
                    .build();

            paymentRepository.save(payment);

            Map<String, Object> response = new HashMap<>();
            response.put("orderId", razorpayOrderId);
            response.put("amount", amount.multiply(new BigDecimal("100")).intValue());
            response.put("currency", "INR");
            response.put("keyId", razorpayKeyId);
            return response;

        } catch (RazorpayException e) {
            throw new RuntimeException("Failed to create Razorpay order: " + e.getMessage());
        }
    }

    @Transactional
    public ApiResponse verifyPayment(User user, PaymentVerifyRequest request) {
        Payment payment = paymentRepository.findByRazorpayOrderId(request.getRazorpayOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (!payment.getUserId().equals(user.getId())) {
            throw new BadRequestException("Unauthorized payment access");
        }

        String expectedSignature = generateSignature(
                request.getRazorpayOrderId(),
                request.getRazorpayPaymentId()
        );

        if (!expectedSignature.equals(request.getRazorpaySignature())) {
            payment.setStatus("FAILED");
            paymentRepository.save(payment);
            throw new BadRequestException("Invalid payment signature");
        }

        payment.setRazorpayPaymentId(request.getRazorpayPaymentId());
        payment.setRazorpaySignature(request.getRazorpaySignature());
        payment.setStatus("COMPLETED");
        paymentRepository.save(payment);

        // Activate premium immediately — don't wait for webhook
        User dbUser = userRepository.findById(payment.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        dbUser.setPremium(true);
        userRepository.save(dbUser);

        // Send invoice if not already sent
        if (!payment.isInvoiceSent()) {
            emailService.sendInvoiceEmail(dbUser.getEmail(), payment);
            payment.setInvoiceSent(true);
            paymentRepository.save(payment);
        }

        return ApiResponse.success("Payment verified successfully");
    }

    public List<PaymentResponse> getPaymentHistory(User user) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ApiResponse activatePremium(User user, PremiumActivateRequest request) {
        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (!payment.getUserId().equals(user.getId())) {
            throw new BadRequestException("Unauthorized payment access");
        }

        if (!"COMPLETED".equals(payment.getStatus())) {
            throw new BadRequestException("Payment not completed");
        }

        User dbUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        dbUser.setPremium(true);
        userRepository.save(dbUser);

        if (!payment.isInvoiceSent()) {
            emailService.sendInvoiceEmail(dbUser.getEmail(), payment);
            payment.setInvoiceSent(true);
            paymentRepository.save(payment);
        }

        return ApiResponse.success("Premium activated successfully");
    }

    public PremiumStatusResponse getPremiumStatus(User user) {
        User dbUser = userRepository.findById(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return PremiumStatusResponse.builder()
                .premium(dbUser.isPremium())
                .source(dbUser.isPremium() ? "PAYMENT" : "FREE")
                .build();
    }

    public Page<PaymentResponse> getAllPayments(int page, int size) {
        return paymentRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size))
                .map(this::toResponse);
    }

    private String generateSignature(String orderId, String paymentId) {
        String data = orderId + "|" + paymentId;
        return new HmacUtils("HmacSHA256", razorpayKeySecret).hmacHex(data);
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .userId(payment.getUserId())
                .razorpayOrderId(payment.getRazorpayOrderId())
                .razorpayPaymentId(payment.getRazorpayPaymentId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .paymentMethod(payment.getPaymentMethod())
                .createdAt(payment.getCreatedAt())
                .build();
    }
}