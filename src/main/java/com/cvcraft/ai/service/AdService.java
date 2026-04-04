package com.cvcraft.ai.service;
import com.cvcraft.ai.dto.AdFlowResponse;
import com.cvcraft.ai.entity.*;
import com.cvcraft.ai.repository.AdEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
public class AdService {
    public static final String STARTED = "STARTED", COMPLETED = "COMPLETED", FAILED = "FAILED";
    private final AdEventRepository adEventRepository;
    public AdService(AdEventRepository r) { this.adEventRepository = r; }
    public AdFlowResponse start(User user) {
        adEventRepository.save(AdEvent.builder().user(user).status(STARTED).usedForExport(false).build());
        return new AdFlowResponse(STARTED, false, "Ad started. Complete to unlock export.");
    }
    @Transactional
    public AdFlowResponse complete(User user) {
        AdEvent e = adEventRepository.findTopByUserAndStatusOrderByCreatedAtDesc(user, STARTED)
                .orElse(AdEvent.builder().user(user).status(STARTED).usedForExport(false).build());
        e.setStatus(COMPLETED); e.setUsedForExport(false); adEventRepository.save(e);
        return new AdFlowResponse(COMPLETED, true, "Ad completed. Free export unlocked.");
    }
    @Transactional
    public AdFlowResponse fail(User user) {
        AdEvent e = adEventRepository.findTopByUserAndStatusOrderByCreatedAtDesc(user, STARTED)
                .orElse(AdEvent.builder().user(user).status(STARTED).usedForExport(false).build());
        e.setStatus(FAILED); e.setUsedForExport(false); adEventRepository.save(e);
        return new AdFlowResponse(FAILED, false, "Ad not completed. Export remains locked.");
    }
}
