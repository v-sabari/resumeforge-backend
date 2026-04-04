package com.cvcraft.ai.service;
import com.cvcraft.ai.dto.*;
import com.cvcraft.ai.entity.Payment;
import com.cvcraft.ai.entity.User;
import com.cvcraft.ai.exception.ApiException;
import com.cvcraft.ai.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class PremiumService {
    private final UserRepository    userRepository;
    private final PaymentRepository paymentRepository;
    public PremiumService(UserRepository ur, PaymentRepository pr) {
        this.userRepository = ur; this.paymentRepository = pr;
    }
    public PremiumStatusResponse status(User user) {
        return new PremiumStatusResponse(user.isPremium(), user.isPremium() ? "PREMIUM" : "FREE",
                user.isPremium() ? "Premium active — unlimited exports enabled." :
                        "Free plan — upgrade for unlimited exports.");
    }
    @Transactional
    public PremiumStatusResponse activate(User user, PremiumActivateRequest request) {
        if (request == null || request.paymentId() == null || request.paymentId().isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "paymentId is required to activate premium");

        Payment payment = paymentRepository.findByPaymentIdAndUser(request.paymentId(), user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment record not found"));

        // SECURITY FIX: Only activate if the Razorpay webhook has already set status to PAID.
        // Without this check, any user who has a payment record (even CREATED/FAILED status)
        // could call this endpoint and self-upgrade without completing payment.
        if (!"PAID".equals(payment.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN,
                    "Payment not yet confirmed by Razorpay. " +
                    "Please wait a few seconds and refresh your dashboard. " +
                    "If this persists, contact support@resumeforge.ai with your payment ID.");
        }

        if (!user.isPremium()) {
            user.setPremium(true);
            userRepository.save(user);
        }
        return new PremiumStatusResponse(true, "PREMIUM", "Premium activated successfully.");
    }
}
