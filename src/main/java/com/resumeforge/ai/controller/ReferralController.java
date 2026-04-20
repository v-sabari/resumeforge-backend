package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.ReferralStatusResponse;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.service.ReferralService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/referral")
public class ReferralController {

    @Autowired
    private ReferralService referralService;

    @GetMapping("/status")
    public ResponseEntity<ReferralStatusResponse> getStatus(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(referralService.getReferralStatus(user));
    }
}
