package com.resumeforge.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "referral_rewards",
        indexes = {
                @Index(name = "idx_referral_rewards_user", columnList = "user_id"),
                @Index(name = "idx_referral_rewards_milestone", columnList = "milestone_count")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_referral_reward_user_milestone", columnNames = {"user_id", "milestone_count"})
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralReward {

    public enum RewardType {
        PREMIUM_DAYS_3,
        ATS_PRO_UNLOCK,
        PREMIUM_DAYS_30
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_type", nullable = false, length = 40)
    private RewardType rewardType;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "milestone_count", nullable = false)
    private Integer milestoneCount;

    @Column(name = "granted_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant grantedAt = Instant.now();

    @Column(name = "expires_at")
    private Instant expiresAt;
}
