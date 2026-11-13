package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.AdFlowLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdFlowLogRepository extends JpaRepository<AdFlowLog, Long> {
    long countByUserIdAndStatus(Long userId, String status);

    // AD-FLOW FIX: completeAd/failAd must attach to a real, still-open flow
    // started by startAd for the same user, instead of inserting a brand-new
    // row that claims a COMPLETED/FAILED status with no matching start event.
    Optional<AdFlowLog> findFirstByUserIdAndStatusOrderByCreatedAtDesc(Long userId, String status);

    // DATA-01 FIX: GDPR export needs to include the user's ad-flow history.
    java.util.List<AdFlowLog> findByUserIdOrderByCreatedAtDesc(Long userId);

    // SEC/BUS FIX: needed for GDPR account deletion (ad_flow_log has a plain
    // userId column with a non-cascading FK to users).
    long deleteByUserId(Long userId);
}
