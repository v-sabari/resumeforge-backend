package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.AiRequest;
import com.resumeforge.ai.dto.AiResponse;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.service.AiService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    @PostMapping("/rewrite")
    public ResponseEntity<AiResponse> rewrite(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return ResponseEntity.ok(aiService.rewriteContent(user, request));
    }

    @PostMapping("/bullets")
    public ResponseEntity<AiResponse> improveBullets(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return ResponseEntity.ok(aiService.improveBullets(user, request));
    }

    @PostMapping("/summary")
    public ResponseEntity<AiResponse> generateSummary(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return ResponseEntity.ok(aiService.generateSummary(user, request));
    }

    @PostMapping("/skills")
    public ResponseEntity<AiResponse> extractSkills(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return ResponseEntity.ok(aiService.extractSkills(user, request));
    }

    @PostMapping("/tailor")
    public ResponseEntity<AiResponse> tailorToJob(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return ResponseEntity.ok(aiService.tailorToJob(user, request));
    }

    @PostMapping("/ats-score")
    public ResponseEntity<AiResponse> atsScore(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return ResponseEntity.ok(aiService.atsScore(user, request));
    }

    @PostMapping("/cover-letter")
    public ResponseEntity<AiResponse> generateCoverLetter(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return ResponseEntity.ok(aiService.generateCoverLetter(user, request));
    }

    @PostMapping("/linkedin")
    public ResponseEntity<AiResponse> optimizeLinkedIn(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return ResponseEntity.ok(aiService.optimizeLinkedIn(user, request));
    }

    @PostMapping("/grammar-check")
    public ResponseEntity<AiResponse> checkGrammar(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return ResponseEntity.ok(aiService.checkGrammar(user, request));
    }

    @PostMapping("/interview-prep")
    public ResponseEntity<AiResponse> generateInterviewPrep(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return ResponseEntity.ok(aiService.generateInterviewPrep(user, request));
    }
}
