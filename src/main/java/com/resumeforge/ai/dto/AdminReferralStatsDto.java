package com.resumeforge.ai.dto;

import java.util.List;

// ── Referral analytics ─────────────────────────────────────────────────────────
public record AdminReferralStatsDto(
        long totalReferrals,
        long qualifiedReferrals,
        long pendingReferrals,
        long qualifiedLast30Days,
        double conversionRate,           // qualified / total as percentage
        List<TopReferrer> topReferrers
) {
    public record TopReferrer(
            Long userId,
            String userName,
            String userEmail,
            long qualifiedCount
    ) {
    }
}
