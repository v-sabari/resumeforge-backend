package com.resumeforge.ai.entity;

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

    @Column(name = "email_otp", length = 20)
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

    public String getPassword() {
        return this.passwordHash;
    }

    public void setPassword(String password) {
        this.passwordHash = password;
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