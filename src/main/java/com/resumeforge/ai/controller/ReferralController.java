package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.ReferralStatusResponse;
import com.resumeforge.ai.service.CurrentUserService;
import com.resumeforge.ai.service.ReferralService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/referral")
public class ReferralController {

    private final ReferralService referralService;
    private final CurrentUserService currentUserService;

    public ReferralController(ReferralService referralService,
                              CurrentUserService currentUserService) {
        this.referralService    = referralService;
        this.currentUserService = currentUserService;
    }

    /**
     * Returns the authenticated user's referral code, share link,
     * qualified/pending counts, full history, and rewards earned.
     */
    @GetMapping("/status")
    public ReferralStatusResponse status() {
        return referralService.getStatus(currentUserService.getCurrentUser());
    }
}
