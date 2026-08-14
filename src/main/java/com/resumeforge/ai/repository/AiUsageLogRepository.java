package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    long countByUserId(Long userId);

    long countByUserIdAndCreatedAtAfter(Long userId, LocalDateTime after);

    @Query("SELECT a.feature, COUNT(a) FROM AiUsageLog a GROUP BY a.feature ORDER BY COUNT(a) DESC")
    List<Object[]> getFeatureUsageStats();

    // NEW: for admin overview + AI tab
    long countByCreatedAtAfter(LocalDateTime after);

    @Query("SELECT COALESCE(SUM(a.inputTokens),0) + COALESCE(SUM(a.outputTokens),0) " +
            "FROM AiUsageLog a WHERE a.createdAt > :after")
    long sumTokensAfter(@Param("after") LocalDateTime after);

    // SEC/BUS FIX: needed for GDPR account deletion (ai_usage_log has a plain
    // userId column with a non-cascading FK to users).
    long deleteByUserId(Long userId);
}