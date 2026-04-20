package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.ReferralReward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReferralRewardRepository extends JpaRepository<ReferralReward, Long> {
    List<ReferralReward> findByUserIdOrderByCreatedAtDesc(Long userId);
    long countByUserIdAndRewardStatus(Long userId, String status);
}
