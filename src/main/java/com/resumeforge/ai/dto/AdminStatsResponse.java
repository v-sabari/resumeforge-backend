package com.resumeforge.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsResponse {
    private long totalUsers;
    private long newUsersLast30Days;
    private long premiumUsers;
    private long verifiedUsers;
    private long totalResumes;

    private long totalPayments;
    private long pendingPayments;
    private long completedPayments;
    private BigDecimal totalRevenue;
    private BigDecimal revenueLast30Days;
    private long totalPaidPayments;
    private long paidPaymentsLast30Days;

    private long totalAiCalls;
    private long aiCallsLast30Days;
    private long totalTokensLast30Days;

    private long totalQualifiedReferrals;
    private long qualifiedReferralsLast30Days;
    private long pendingReferrals;
}