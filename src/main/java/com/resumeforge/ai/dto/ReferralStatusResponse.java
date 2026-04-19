package com.resumeforge.ai.dto;

import java.time.Instant;
import java.util.List;

/**
 * Full referral status for the authenticated user.
 * Returned by GET /api/referral/status
 */
public record ReferralStatusResponse(
        /** This user's personal referral code. */
        String referralCode,
        /** Full share URL: https://resumeforgeai.site/register?ref=CODE */
        String referralLink,
        /** Total referrals that have qualified (email verified + resume created). */
        long qualifiedReferrals,
        /** Referrals still in PENDING state. */
        long pendingReferrals,
        /** All referral history entries. */
        List<ReferralHistoryItem> history,
        /** All rewards granted to this user. */
        List<RewardItem> rewards,
        /** Next milestone and what it unlocks. */
        NextMilestone nextMilestone
) {
    public record ReferralHistoryItem(
            Long referralId,
            String referredUserEmail,   // masked: j***@gmail.com
            String status,
            Instant createdAt,
            Instant qualifiedAt
    ) {}

    public record RewardItem(
            String rewardType,
            String description,
            int milestoneCount,
            Instant grantedAt,
            Instant expiresAt           // null for permanent rewards
    ) {}

    public record NextMilestone(
            int referralsNeeded,
            int referralsRemaining,
            String reward
    ) {}
}
