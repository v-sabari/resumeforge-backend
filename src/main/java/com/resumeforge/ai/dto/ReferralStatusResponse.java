package com.resumeforge.ai.dto;

import java.util.List;

public record ReferralStatusResponse(
        String referralCode,
        String referralLink,
        long qualifiedReferrals,
        long pendingReferrals,
        List<ReferralHistoryResponse> history,
        List<ReferralRewardResponse> rewards,
        NextMilestoneResponse nextMilestone
) {
}
