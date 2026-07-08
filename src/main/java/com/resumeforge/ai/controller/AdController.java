package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.ApiResponse;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.service.AdService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ads")
public class AdController {

    @Autowired
    private AdService adService;

    @PostMapping("/start")
    public ResponseEntity<ApiResponse> startAd(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(adService.startAd(user));
    }

    @PostMapping("/complete")
    public ResponseEntity<ApiResponse> completeAd(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(adService.completeAd(user));
    }

    @PostMapping("/fail")
    public ResponseEntity<ApiResponse> failAd(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(adService.failAd(user));
    }
}
