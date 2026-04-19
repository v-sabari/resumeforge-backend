package com.resumeforge.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * Records every AI feature invocation per user.
 *
 * Used for:
 *   - Admin analytics (which features are popular, AI cost tracking)
 *   - Per-feature daily usage limits (e.g. ATS score: 3/day for free users)
 *   - Future billing/metering if AI moves to usage-based pricing
 *
 * Lightweight: written asynchronously after the AI call succeeds.
 * A failed AI call is NOT logged (only successful completions count toward limits).
 */
@Entity
@Table(name = "ai_usage_logs",
        indexes = {
                @Index(name = "idx_ai_usage_user_feature_created",
                        columnList = "user_id, feature, created_at DESC"),
                @Index(name = "idx_ai_usage_created",
                        columnList = "created_at DESC")
        })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiUsageLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /**
     * Short identifier for the AI feature.
     * Values: summary | bullets | skills | rewrite | ats_score | cover_letter
     *         | tailor | linkedin | interview_prep | grammar_check
     */
    @Column(nullable = false, length = 40)
    private String feature;

    /**
     * Approximate token count for cost tracking.
     * Populated from the OpenRouter response when available.
     */
    @Column(name = "tokens_used")
    private Integer tokensUsed;

    /**
     * Whether the user was on premium at the time of the call.
     * Useful for analytics and revenue attribution.
     */
    @Column(name = "was_premium", nullable = false)
    @Builder.Default
    private boolean wasPremium = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
