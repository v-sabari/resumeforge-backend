package com.resumeforge.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminStatsResponse {
    private long totalUsers;
    private long premiumUsers;
    private long verifiedUsers;
    private long totalResumes;
    private long totalPayments;
    private long pendingPayments;
    private long completedPayments;
}
