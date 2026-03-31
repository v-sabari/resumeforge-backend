package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.AdFlowResponse;
import com.resumeforge.ai.service.AdService;
import com.resumeforge.ai.service.CurrentUserService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ads")
public class AdsController {
    private final AdService adService;
    private final CurrentUserService currentUserService;

    public AdsController(AdService adService, CurrentUserService currentUserService) {
        this.adService = adService;
        this.currentUserService = currentUserService;
    }

    @PostMapping("/start")
    public AdFlowResponse start() {
        return adService.start(currentUserService.getCurrentUser());
    }

    @PostMapping("/complete")
    public AdFlowResponse complete() {
        return adService.complete(currentUserService.getCurrentUser());
    }

    @PostMapping("/fail")
    public AdFlowResponse fail() {
        return adService.fail(currentUserService.getCurrentUser());
    }
}
