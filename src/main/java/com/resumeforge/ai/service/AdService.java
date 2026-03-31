package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.AdFlowResponse;
import com.resumeforge.ai.entity.AdEvent;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.repository.AdEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdService {
    public static final String STARTED = "STARTED";
    public static final String COMPLETED = "COMPLETED";
    public static final String FAILED = "FAILED";

    private final AdEventRepository adEventRepository;

    public AdService(AdEventRepository adEventRepository) {
        this.adEventRepository = adEventRepository;
    }

    public AdFlowResponse start(User user) {
        adEventRepository.save(AdEvent.builder().user(user).status(STARTED).usedForExport(false).build());
        return new AdFlowResponse(STARTED, false, "Ad flow started. Complete the ad to unlock this export.");
    }

    @Transactional
    public AdFlowResponse complete(User user) {
        AdEvent event = adEventRepository.findTopByUserAndStatusOrderByCreatedAtDesc(user, STARTED)
                .orElse(AdEvent.builder().user(user).status(STARTED).usedForExport(false).build());
        event.setStatus(COMPLETED);
        event.setUsedForExport(false);
        adEventRepository.save(event);
        return new AdFlowResponse(COMPLETED, true, "Ad completed successfully. Your next free export is unlocked.");
    }

    @Transactional
    public AdFlowResponse fail(User user) {
        AdEvent event = adEventRepository.findTopByUserAndStatusOrderByCreatedAtDesc(user, STARTED)
                .orElse(AdEvent.builder().user(user).status(STARTED).usedForExport(false).build());
        event.setStatus(FAILED);
        event.setUsedForExport(false);
        adEventRepository.save(event);
        return new AdFlowResponse(FAILED, false, "Ad was not completed. Free export remains locked.");
    }

    public boolean hasUnusedCompletedAd(User user) {
        return adEventRepository.findTopByUserAndStatusAndUsedForExportFalseOrderByCreatedAtDesc(user, COMPLETED).isPresent();
    }

    @Transactional
    public boolean consumeCompletedAd(User user) {
        return adEventRepository.findTopByUserAndStatusAndUsedForExportFalseOrderByCreatedAtDesc(user, COMPLETED)
                .map(event -> {
                    event.setUsedForExport(true);
                    adEventRepository.save(event);
                    return true;
                }).orElse(false);
    }
}
