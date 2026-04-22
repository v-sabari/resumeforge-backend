package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.ReferralStatusResponse;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.ApiException;
import com.resumeforge.ai.repository.UserRepository;
import com.resumeforge.ai.service.ReferralService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/referral")
public class ReferralController {
    private final ReferralService referralService;
    private final UserRepository userRepository;

    public ReferralController(ReferralService referralService, UserRepository userRepository) {
        this.referralService = referralService;
        this.userRepository = userRepository;
    }

    @GetMapping("/status")
    public ReferralStatusResponse status(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        return referralService.getReferralStatus(user);
    }
}
