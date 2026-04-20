package com.resumeforge.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralRewardResponse {
    private Long id;
    private String rewardType;
    private BigDecimal rewardValue;
    private String rewardStatus;
    private LocalDateTime createdAt;
}
