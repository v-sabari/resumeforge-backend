package com.resumeforge.ai.dto;

import java.time.Instant;

public record ReferralRewardResponse(
        String rewardType,
        String description,
        Integer milestoneCount,
        Instant grantedAt,
        Instant expiresAt
) {
}
