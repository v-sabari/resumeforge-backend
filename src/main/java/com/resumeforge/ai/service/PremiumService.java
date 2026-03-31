package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.PremiumActivateRequest;
import com.resumeforge.ai.dto.PremiumStatusResponse;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.ApiException;
import com.resumeforge.ai.repository.PaymentRepository;
import com.resumeforge.ai.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PremiumService {
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;

    public PremiumService(UserRepository userRepository, PaymentRepository paymentRepository) {
        this.userRepository = userRepository;
        this.paymentRepository = paymentRepository;
    }

    public PremiumStatusResponse status(User user) {
        return new PremiumStatusResponse(user.isPremium(), user.isPremium() ? "PREMIUM" : "FREE",
                user.isPremium() ? "Premium is active. Unlimited exports are enabled." : "Free plan active. Ads are required for each free export.");
    }

    @Transactional
    public PremiumStatusResponse activate(User user, PremiumActivateRequest request) {
        if (request == null || request.paymentId() == null || request.paymentId().isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "paymentId is required to activate premium");
        }
        paymentRepository.findByPaymentIdAndUser(request.paymentId(), user)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Payment record not found"));
        user.setPremium(true);
        userRepository.save(user);
        return new PremiumStatusResponse(true, "PREMIUM", "Premium activated successfully.");
    }
}
