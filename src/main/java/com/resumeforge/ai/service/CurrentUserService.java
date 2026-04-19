package com.resumeforge.ai.service;

import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.UnauthorizedException;
import com.resumeforge.ai.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class CurrentUserService {

    private static final Logger log = LoggerFactory.getLogger(CurrentUserService.class);

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Returns the authenticated user, enforcing lazy premium expiry.
     *
     * If the user has time-limited premium (premiumExpiresAt is set)
     * and it has passed, we clear the premium flag and persist it here.
     * This avoids a separate scheduled job and ensures expiry is always
     * current when the user makes a request.
     */
    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new UnauthorizedException("Authentication required");
        }

        User user = userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new UnauthorizedException("Authenticated user not found"));

        // ── Lazy premium expiry ───────────────────────────────────────
        // Only check/persist if user has time-limited premium.
        // If premiumExpiresAt is null, premium is lifetime (from payment) — skip.
        if (user.isPremium() && user.getPremiumExpiresAt() != null
                && Instant.now().isAfter(user.getPremiumExpiresAt())) {
            user.setPremium(false);
            user.setPremiumExpiresAt(null);
            userRepository.save(user);
            log.info("Time-limited premium expired and cleared for userId={}", user.getId());
        }

        return user;
    }
}
