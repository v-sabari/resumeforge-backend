package com.cvcraft.ai.controller;
import com.cvcraft.ai.dto.*;
import com.cvcraft.ai.service.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/premium")
public class PremiumController {
    private final PremiumService premiumService;
    private final CurrentUserService currentUserService;
    public PremiumController(PremiumService p, CurrentUserService c) { this.premiumService = p; this.currentUserService = c; }
    @GetMapping("/status") public PremiumStatusResponse status() { return premiumService.status(currentUserService.getCurrentUser()); }
    @PostMapping("/activate") public PremiumStatusResponse activate(@RequestBody PremiumActivateRequest req) { return premiumService.activate(currentUserService.getCurrentUser(), req); }
}
