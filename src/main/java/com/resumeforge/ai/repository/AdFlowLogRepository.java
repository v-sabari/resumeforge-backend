package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.AdFlowLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AdFlowLogRepository extends JpaRepository<AdFlowLog, Long> {
    long countByUserIdAndStatus(Long userId, String status);
}
