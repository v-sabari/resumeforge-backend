package com.resumeforge.ai;

import com.resumeforge.ai.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression guard for the premium access model (SEC FIX in {@link User}).
 *
 * {@code isPremium()} is the single source of truth for every premium gate.
 * Referral rewards and promo grants set {@code premium = true} with a finite
 * {@code premiumExpiresAt}; paid/admin grants leave it null (permanent). The
 * method must return false once a set expiry has elapsed, otherwise limited
 * referral premium silently becomes lifetime.
 */
class UserPremiumExpiryTest {

    @Test
    void nonPremiumUserIsNeverPremium() {
        User user = User.builder().premium(false).premiumExpiresAt(null).build();
        assertFalse(user.isPremium());
    }

    @Test
    void nullExpiryMeansPermanentPremium() {
        User user = User.builder().premium(true).premiumExpiresAt(null).build();
        assertTrue(user.isPremium(), "A paid/admin grant (no expiry) is lifetime premium");
    }

    @Test
    void futureExpiryIsStillPremium() {
        User user = User.builder()
                .premium(true)
                .premiumExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS))
                .build();
        assertTrue(user.isPremium());
    }

    @Test
    void pastExpiryIsNoLongerPremium() {
        User user = User.builder()
                .premium(true)
                .premiumExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .build();
        assertFalse(user.isPremium(), "Expired time-limited premium must not grant access");
    }
}
