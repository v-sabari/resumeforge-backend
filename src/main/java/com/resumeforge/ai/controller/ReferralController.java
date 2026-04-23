package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.ReferralStatusResponse;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.ResourceNotFoundException;
import com.resumeforge.ai.exception.UnauthorizedException;
import com.resumeforge.ai.repository.UserRepository;
import com.resumeforge.ai.service.ReferralService;
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
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new UnauthorizedException("Authentication required");
        }

        Object principal = authentication.getPrincipal();
        User user;

        if (principal instanceof User authenticatedUser) {
            user = userRepository.findById(authenticatedUser.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        } else {
            String email = authentication.getName();

            if (email == null || email.isBlank()) {
                throw new UnauthorizedException("Authentication required");
            }

            user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        }

        return referralService.getReferralStatus(user);
    }
}