package com.resumeforge.ai.dto;

public record NextMilestoneResponse(
        Integer referralsNeeded,
        Integer referralsRemaining,
        String reward
) {
}
