package com.resumeforge.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralStatusResponse {
    private String referralCode;
    private String referralLink;
    private long totalReferrals;
    private long verifiedReferrals;
    private long pendingRewards;
    private long claimedRewards;
    private List<ReferralRewardResponse> rewards;
}
