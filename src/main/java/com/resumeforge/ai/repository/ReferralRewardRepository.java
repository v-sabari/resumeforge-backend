package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.ReferralReward;
import com.resumeforge.ai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReferralRewardRepository extends JpaRepository<ReferralReward, Long> {
    List<ReferralReward> findByUserOrderByGrantedAtDesc(User user);
    Optional<ReferralReward> findByUserAndMilestoneCount(User user, Integer milestoneCount);
}
