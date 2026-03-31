package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.PremiumActivateRequest;
import com.resumeforge.ai.dto.PremiumStatusResponse;
import com.resumeforge.ai.service.CurrentUserService;
import com.resumeforge.ai.service.PremiumService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/premium")
public class PremiumController {
    private final PremiumService premiumService;
    private final CurrentUserService currentUserService;

    public PremiumController(PremiumService premiumService, CurrentUserService currentUserService) {
        this.premiumService = premiumService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/status")
    public PremiumStatusResponse status() {
        return premiumService.status(currentUserService.getCurrentUser());
    }

    @PostMapping("/activate")
    public PremiumStatusResponse activate(@RequestBody PremiumActivateRequest request) {
        return premiumService.activate(currentUserService.getCurrentUser(), request);
    }
}
