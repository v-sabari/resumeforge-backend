package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.AiUsageLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {
    long countByUserId(Long userId);
    
    @Query("SELECT a.feature, COUNT(a) FROM AiUsageLog a GROUP BY a.feature ORDER BY COUNT(a) DESC")
    List<Object[]> getFeatureUsageStats();
}
