package com.cvcraft.ai.controller;
import com.cvcraft.ai.dto.AdFlowResponse;
import com.cvcraft.ai.service.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/ads")
public class AdsController {
    private final AdService adService;
    private final CurrentUserService currentUserService;
    public AdsController(AdService a, CurrentUserService c) { this.adService = a; this.currentUserService = c; }
    @PostMapping("/start")    public AdFlowResponse start()    { return adService.start(currentUserService.getCurrentUser()); }
    @PostMapping("/complete") public AdFlowResponse complete() { return adService.complete(currentUserService.getCurrentUser()); }
    @PostMapping("/fail")     public AdFlowResponse fail()     { return adService.fail(currentUserService.getCurrentUser()); }
}
