package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.Referral;
import com.resumeforge.ai.entity.ReferralReward;
import com.resumeforge.ai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReferralRewardRepository extends JpaRepository<ReferralReward, Long> {

    /** All rewards for a user, newest first — for the user's reward history. */
    List<ReferralReward> findByUserOrderByGrantedAtDesc(User user);

    /**
     * Check if a specific reward type was already granted for a specific referral.
     * Prevents double-granting the same reward for the same qualifying referral.
     */
    boolean existsByReferralAndRewardType(Referral referral, String rewardType);

    /**
     * Check if a milestone reward was already issued at a given count.
     * Prevents re-issuing the same milestone reward if the count is recalculated.
     */
    boolean existsByUserAndMilestoneCount(User user, int milestoneCount);
}
