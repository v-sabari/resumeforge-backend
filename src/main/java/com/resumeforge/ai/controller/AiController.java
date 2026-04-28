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

import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    @PostMapping("/rewrite")
    public CompletableFuture<ResponseEntity<AiResponse>> rewrite(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return aiService.rewriteContent(user, request)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/bullets")
    public CompletableFuture<ResponseEntity<AiResponse>> improveBullets(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return aiService.improveBullets(user, request)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/summary")
    public CompletableFuture<ResponseEntity<AiResponse>> generateSummary(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return aiService.generateSummary(user, request)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/skills")
    public CompletableFuture<ResponseEntity<AiResponse>> extractSkills(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return aiService.extractSkills(user, request)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/tailor")
    public CompletableFuture<ResponseEntity<AiResponse>> tailorToJob(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return aiService.tailorToJob(user, request)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/ats-score")
    public CompletableFuture<ResponseEntity<AiResponse>> atsScore(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return aiService.atsScore(user, request)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/cover-letter")
    public CompletableFuture<ResponseEntity<AiResponse>> generateCoverLetter(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return aiService.generateCoverLetter(user, request)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/linkedin")
    public CompletableFuture<ResponseEntity<AiResponse>> optimizeLinkedIn(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return aiService.optimizeLinkedIn(user, request)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/grammar-check")
    public CompletableFuture<ResponseEntity<AiResponse>> checkGrammar(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return aiService.checkGrammar(user, request)
                .thenApply(ResponseEntity::ok);
    }

    @PostMapping("/interview-prep")
    public CompletableFuture<ResponseEntity<AiResponse>> generateInterviewPrep(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        return aiService.generateInterviewPrep(user, request)
                .thenApply(ResponseEntity::ok);
    }
}