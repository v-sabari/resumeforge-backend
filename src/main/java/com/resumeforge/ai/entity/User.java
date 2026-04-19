package com.resumeforge.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "is_premium", nullable = false)
    @Builder.Default
    private boolean premium = false;

    /**
     * When set, this user has time-limited premium (referral reward).
     * Null = lifetime premium (from payment) or no premium.
     * Expiry enforced lazily in CurrentUserService.getCurrentUser().
     */
    @Column(name = "premium_expires_at")
    private Instant premiumExpiresAt;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = false;

    /**
     * Unique 8-char referral code. Generated at registration.
     * Uppercase alphanumeric, no ambiguous characters.
     */
    @Column(name = "referral_code", unique = true, length = 12)
    private String referralCode;

    /**
     * User role — controls access to admin endpoints.
     * Values: "USER" (default) | "ADMIN"
     * Spring Security uses this as ROLE_USER / ROLE_ADMIN.
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String role = "USER";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Resume> resumes = new ArrayList<>();

    /** True if this user has active premium right now (handles lifetime and time-limited). */
    public boolean isPremiumActive() {
        if (!premium) return false;
        if (premiumExpiresAt == null) return true;
        return Instant.now().isBefore(premiumExpiresAt);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
