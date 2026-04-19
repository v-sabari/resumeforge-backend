package com.resumeforge.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * A reward granted to a referrer when a referral qualifies.
 *
 * Reward types:
 *   PREMIUM_DAYS_3   — 3 days of premium access (1st qualified referral)
 *   ATS_PRO_UNLOCK   — unlocks the ATS Pro Scan feature (3rd qualified referral)
 *   PREMIUM_DAYS_30  — 30 days of premium access (5th qualified referral)
 *
 * Multiple rewards can be issued to the same user over time.
 * Each reward is tied to the referral that triggered it and the
 * qualified referral count milestone it represents.
 */
@Entity
@Table(name = "referral_rewards",
        indexes = {
                @Index(name = "idx_referral_rewards_user",
                        columnList = "user_id, granted_at DESC")
        })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReferralReward {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The referrer who earned this reward. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * The referral that triggered this reward.
     * Allows deduplication: one reward per referral per type.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "referral_id", nullable = false)
    private Referral referral;

    /** PREMIUM_DAYS_3 | ATS_PRO_UNLOCK | PREMIUM_DAYS_30 */
    @Column(name = "reward_type", nullable = false, length = 30)
    private String rewardType;

    /** Human-readable description of the reward. */
    @Column(name = "reward_description", length = 200)
    private String rewardDescription;

    /**
     * Which milestone triggered this reward.
     * 1 = first referral, 3 = third referral, 5 = fifth referral.
     */
    @Column(name = "milestone_count", nullable = false)
    private int milestoneCount;

    @Column(name = "granted_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant grantedAt = Instant.now();

    /** For time-limited rewards: when the premium extension expires. */
    @Column(name = "expires_at")
    private Instant expiresAt;
}
