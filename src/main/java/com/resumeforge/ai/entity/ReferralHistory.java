package com.resumeforge.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "referral_history",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_referral_history_referred_user", columnNames = "referred_user_id")
        },
        indexes = {
                @Index(name = "idx_referral_history_referrer", columnList = "referrer_user_id"),
                @Index(name = "idx_referral_history_referred", columnList = "referred_user_id"),
                @Index(name = "idx_referral_history_status", columnList = "status")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralHistory {

    public enum ReferralStatus {
        PENDING,
        QUALIFIED,
        REJECTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referrer_user_id", nullable = false)
    private User referrerUser;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referred_user_id", nullable = false)
    private User referredUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReferralStatus status = ReferralStatus.PENDING;

    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    @Column(name = "resume_created", nullable = false)
    @Builder.Default
    private boolean resumeCreated = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "qualified_at")
    private Instant qualifiedAt;
}
