package com.resumeforge.ai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @JsonIgnore
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "role", nullable = false, length = 30)
    @Builder.Default
    private String role = "USER";

    @Column(name = "is_premium", nullable = false)
    @Builder.Default
    private boolean premium = false;

    @Column(name = "premium_expires_at")
    private Instant premiumExpiresAt;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    // SEC FIX: stores the SHA-256 hex digest of the emailed OTP (never the
    // plaintext OTP). 64 hex chars — widened from 20 via migration V23.
    @Column(name = "email_otp", length = 64)
    private String emailOtp;

    @Column(name = "email_otp_expires_at")
    private LocalDateTime emailOtpExpiresAt;

    @Column(name = "password_reset_token", length = 255)
    private String passwordResetToken;

    @Column(name = "password_reset_expires_at")
    private LocalDateTime passwordResetExpiresAt;

    @Column(name = "referral_code", unique = true, length = 12)
    private String referralCode;

    @Column(name = "referred_by_user_id")
    private Long referredByUserId;

    @Column(name = "has_created_resume", nullable = false)
    @Builder.Default
    private boolean hasCreatedResume = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * Stamped whenever a security-sensitive action invalidates prior sessions
     * (password reset, future: forced logout). Any JWT whose iat claim is before
     * this value is rejected in JwtAuthenticationFilter, even if its signature
     * and expiry are valid.
     */
    @Column(name = "token_issued_at")
    private Instant tokenIssuedAt;

    /**
     * SEC FIX: the password hash must never be serialized into any JSON
     * response (e.g. if a User were ever returned directly). Lombok's @Getter
     * would normally generate getPasswordHash(); we shadow it here with an
     * explicitly @JsonIgnore-annotated getter so Jackson always skips it.
     */
    @JsonIgnore
    public String getPasswordHash() {
        return this.passwordHash;
    }

    @JsonIgnore
    public String getPassword() {
        return this.passwordHash;
    }

    public void setPassword(String password) {
        this.passwordHash = password;
    }

    /**
     * SEC FIX: premium status must respect premiumExpiresAt. Referral rewards
     * grant time-limited premium (3/30 days) by setting premiumExpiresAt, but
     * the raw {@code premium} flag was read directly everywhere — so referral
     * premium was effectively lifetime. isPremium() is the single source of
     * truth used by every service (ExportService, AiService, AuthService,
     * PaymentService, AdminService) and now returns false once a set expiry
     * has passed. A null expiry means permanent premium (paid/admin grant).
     */
    public boolean isPremium() {
        if (!this.premium) return false;
        if (this.premiumExpiresAt == null) return true;
        return this.premiumExpiresAt.isAfter(Instant.now());
    }

    public boolean isHasCreatedResume() {
        return this.hasCreatedResume;
    }

    public static class UserBuilder {
        public UserBuilder password(String password) {
            this.passwordHash = password;
            return this;
        }
    }

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.role == null || this.role.isBlank()) {
            this.role = "USER";
        }
    }
}