package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.ReferralRewardResponse;
import com.resumeforge.ai.dto.ReferralStatusResponse;
import com.resumeforge.ai.entity.ReferralReward;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.repository.ReferralRewardRepository;
import com.resumeforge.ai.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReferralService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReferralRewardRepository referralRewardRepository;

    @Value("${app.frontend.base-url:https://www.resumeforgeai.site}")
    private String frontendBaseUrl;

    public ReferralStatusResponse getReferralStatus(User user) {
        long totalReferrals = userRepository.countVerifiedReferrals(user.getId());
        long pendingRewards = referralRewardRepository.countByUserIdAndRewardStatus(user.getId(), "PENDING");
        long claimedRewards = referralRewardRepository.countByUserIdAndRewardStatus(user.getId(), "CLAIMED");

        List<ReferralRewardResponse> rewards = referralRewardRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toRewardResponse)
                .collect(Collectors.toList());

        // Generate referral link
        String referralLink = frontendBaseUrl + "/register?ref=" + user.getReferralCode();

        return ReferralStatusResponse.builder()
                .referralCode(user.getReferralCode())
                .referralLink(referralLink)
                .totalReferrals(totalReferrals)
                .verifiedReferrals(totalReferrals)
                .pendingRewards(pendingRewards)
                .claimedRewards(claimedRewards)
                .rewards(rewards)
                .build();
    }

    private ReferralRewardResponse toRewardResponse(ReferralReward reward) {
        return ReferralRewardResponse.builder()
                .id(reward.getId())
                .rewardType(reward.getRewardType())
                .rewardValue(reward.getRewardValue())
                .rewardStatus(reward.getRewardStatus())
                .createdAt(reward.getCreatedAt())
                .build();
    }
}
