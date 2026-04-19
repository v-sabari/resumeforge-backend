package com.resumeforge.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "referrals",
        indexes = {
                @Index(name = "idx_referrals_referrer", columnList = "referrer_id, status"),
                @Index(name = "idx_referrals_referred", columnList = "referred_user_id"),
                @Index(name = "idx_referrals_code",     columnList = "referral_code_used")
        })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Referral {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user who shared their referral code. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referrer_id", nullable = false)
    private User referrer;

    /**
     * The user who signed up using the code.
     * UNIQUE — a user can only be referred once.
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referred_user_id", nullable = false, unique = true)
    private User referredUser;

    /** Snapshot of the code used at sign-up time. */
    @Column(name = "referral_code_used", nullable = false, length = 12)
    private String referralCodeUsed;

    /**
     * PENDING   — signed up but not yet qualified
     * QUALIFIED — email verified + first resume created → rewards issued
     * REJECTED  — anti-abuse override
     */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @Column(name = "qualified_at")
    private Instant qualifiedAt;
}
