package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.ApiResponse;
import com.resumeforge.ai.entity.AdFlowLog;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.BadRequestException;
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
        // AD-FLOW FIX: only a flow that was actually started can be completed.
        // Previously this inserted a standalone COMPLETED row, so a caller
        // could claim a reward without ever starting — or watching — an ad,
        // and the audit trail contained no matching STARTED event.
        AdFlowLog log = adFlowLogRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), "STARTED")
                .orElseThrow(() -> new BadRequestException(
                        "No started ad flow to complete. Start an ad first."));
        log.setStatus("COMPLETED");
        adFlowLogRepository.save(log);
        
        return ApiResponse.success("Ad completed");
    }

    @Transactional
    public ApiResponse failAd(User user) {
        // AD-FLOW FIX: same rule as completeAd — a failure can only be
        // recorded against an open STARTED flow for this user.
        AdFlowLog log = adFlowLogRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(user.getId(), "STARTED")
                .orElseThrow(() -> new BadRequestException(
                        "No started ad flow to fail. Start an ad first."));
        log.setStatus("FAILED");
        adFlowLogRepository.save(log);
        
        return ApiResponse.success("Ad failed");
    }
}
