package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.AiUsageLog;
import com.resumeforge.ai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    /** Per-user daily limit enforcement. */
    @Query("""
           SELECT COUNT(l) FROM AiUsageLog l
           WHERE l.user = :user AND l.feature = :feature AND l.createdAt >= :since
           """)
    long countUsageSince(@Param("user") User user,
                         @Param("feature") String feature,
                         @Param("since") Instant since);

    // ── Admin analytics ────────────────────────────────────────────────

    /** Total calls across all features since a given instant. */
    long countByCreatedAtAfter(Instant since);

    /** Total calls for a specific feature since a given instant. */
    @Query("SELECT COUNT(l) FROM AiUsageLog l WHERE l.feature = :feature AND l.createdAt >= :since")
    long countFeatureUsageSince(@Param("feature") String feature, @Param("since") Instant since);

    /**
     * Feature usage breakdown: [{feature, callCount}] sorted by count desc.
     * Used to build the feature popularity chart in the admin panel.
     */
    @Query("""
           SELECT l.feature AS feature, COUNT(l) AS callCount
           FROM AiUsageLog l
           WHERE l.createdAt >= :since
           GROUP BY l.feature
           ORDER BY COUNT(l) DESC
           """)
    List<FeatureUsageRow> featureBreakdownSince(@Param("since") Instant since);

    /** Total tokens used (for cost estimation). */
    @Query("SELECT COALESCE(SUM(l.tokensUsed), 0) FROM AiUsageLog l WHERE l.createdAt >= :since")
    long totalTokensSince(@Param("since") Instant since);

    /** Projection interface for feature breakdown query. */
    interface FeatureUsageRow {
        String getFeature();
        Long getCallCount();
    }
}
