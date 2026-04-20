package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.ApiResponse;
import com.resumeforge.ai.entity.AdFlowLog;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.repository.AdFlowLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdService {

    @Autowired
    private AdFlowLogRepository adFlowLogRepository;

    @Transactional
    public ApiResponse startAd(User user) {
        AdFlowLog log = AdFlowLog.builder()
                .userId(user.getId())
                .adType("REWARDED")
                .status("STARTED")
                .build();
        adFlowLogRepository.save(log);
        
        return ApiResponse.success("Ad started");
    }

    @Transactional
    public ApiResponse completeAd(User user) {
        AdFlowLog log = AdFlowLog.builder()
                .userId(user.getId())
                .adType("REWARDED")
                .status("COMPLETED")
                .build();
        adFlowLogRepository.save(log);
        
        return ApiResponse.success("Ad completed");
    }

    @Transactional
    public ApiResponse failAd(User user) {
        AdFlowLog log = AdFlowLog.builder()
                .userId(user.getId())
                .adType("REWARDED")
                .status("FAILED")
                .build();
        adFlowLogRepository.save(log);
        
        return ApiResponse.success("Ad failed");
    }
}
