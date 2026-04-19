package com.resumeforge.ai.dto;

import java.math.BigDecimal;

// ── Dashboard stats ────────────────────────────────────────────────────────────
public record AdminStatsDto(
        long totalUsers,
        long newUsersLast30Days,
        long premiumUsers,
        long verifiedUsers,
        BigDecimal totalRevenue,
        BigDecimal revenueLast30Days,
        long totalPaidPayments,
        long paidPaymentsLast30Days,
        long totalAiCalls,
        long aiCallsLast30Days,
        long totalTokensLast30Days,
        long totalQualifiedReferrals,
        long qualifiedReferralsLast30Days,
        long pendingReferrals
) {
}
