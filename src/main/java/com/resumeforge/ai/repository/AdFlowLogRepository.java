package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.AdFlowLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdFlowLogRepository extends JpaRepository<AdFlowLog, Long> {
    long countByUserIdAndStatus(Long userId, String status);

    // SEC/BUS FIX: needed for GDPR account deletion (ad_flow_log has a plain
    // userId column with a non-cascading FK to users).
    long deleteByUserId(Long userId);
}
