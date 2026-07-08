package com.resumeforge.ai.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.resumeforge.ai.dto.AiRequest;
import com.resumeforge.ai.dto.ApiResponse;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.AiException;
import com.resumeforge.ai.exception.RateLimitException;
import com.resumeforge.ai.service.AiService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

/**
 * AI-01 FIX — AiController improvements:
 *
 * 1. Null-user guard on every endpoint: if the JWT filter failed silently and
 *    injected null for @AuthenticationPrincipal, we now return 401 immediately
 *    instead of NPE → 500.
 *
 * 2. handle() correctly extracts AiException.ErrorCode from the async
 *    CompletableFuture so the structured errorCode field reaches the client.
 *    Previously all async failures were mapped to 500 with no errorCode.
 *
 * AI-02 FIX:
 * 3. handle() now takes a {@code CompletableFuture<JsonNode>} instead of
 *    {@code CompletableFuture<AiResponse>}. AiService no longer wraps the
 *    model's structured output in the old flat {result, inputTokens,
 *    outputTokens} DTO — it returns the parsed, feature-specific JSON
 *    (e.g. {items:[...]}, {score, grade, ...}) straight through. Spring's
 *    default Jackson message converter serializes a JsonNode natively, so
 *    the client receives exactly the shape aiService.js / AIActionPanel.jsx
 *    expect.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    private CompletableFuture<ResponseEntity<Object>> handle(
            CompletableFuture<JsonNode> future) {

        return future.handle((result, ex) -> {
            if (ex == null) {
                return ResponseEntity.ok((Object) result);
            }

            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;

            if (cause instanceof AiException aie) {
                HttpStatus status = switch (aie.getErrorCode()) {
                    case OPENROUTER_AUTH_ERROR     -> HttpStatus.BAD_GATEWAY;
                    case OPENROUTER_FORBIDDEN      -> HttpStatus.BAD_GATEWAY;
                    case OPENROUTER_RATE_LIMIT     -> HttpStatus.TOO_MANY_REQUESTS;
                    case OPENROUTER_UNAVAILABLE    -> HttpStatus.SERVICE_UNAVAILABLE;
                    case OPENROUTER_EMPTY_RESPONSE -> HttpStatus.BAD_GATEWAY;
                    case AI_SERVICE_ERROR          -> HttpStatus.INTERNAL_SERVER_ERROR;
                };
                return ResponseEntity
                        .status(status)
                        .<Object>body(ApiResponse.error(
                                aie.getMessage(),
                                aie.getErrorCode().name()));
            }

            if (cause instanceof RateLimitException rle) {
                return ResponseEntity
                        .status(HttpStatus.TOO_MANY_REQUESTS)
                        .<Object>body(ApiResponse.error(rle.getMessage()));
            }

            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .<Object>body(ApiResponse.error(
                            cause.getMessage() != null
                                    ? cause.getMessage()
                                    : "AI service error"));
        });
    }

    private ResponseEntity<Object> rejectIfUnauthenticated(User user) {
        if (user == null) {
            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(
                            "Authentication required. Please log in and include your Bearer token.",
                            "AUTH_REQUIRED"));
        }
        return null;
    }

    // ── Endpoints ─────────────────────────────────────────────────────────────

    @PostMapping("/rewrite")
    public CompletableFuture<ResponseEntity<Object>> rewrite(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return CompletableFuture.completedFuture(guard);
        return handle(aiService.rewriteContent(user, request));
    }

    @PostMapping("/bullets")
    public CompletableFuture<ResponseEntity<Object>> improveBullets(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return CompletableFuture.completedFuture(guard);
        return handle(aiService.improveBullets(user, request));
    }

    @PostMapping("/summary")
    public CompletableFuture<ResponseEntity<Object>> generateSummary(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return CompletableFuture.completedFuture(guard);
        return handle(aiService.generateSummary(user, request));
    }

    @PostMapping("/skills")
    public CompletableFuture<ResponseEntity<Object>> extractSkills(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return CompletableFuture.completedFuture(guard);
        return handle(aiService.extractSkills(user, request));
    }

    @PostMapping("/tailor")
    public CompletableFuture<ResponseEntity<Object>> tailorToJob(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return CompletableFuture.completedFuture(guard);
        return handle(aiService.tailorToJob(user, request));
    }

    @PostMapping("/ats-score")
    public CompletableFuture<ResponseEntity<Object>> atsScore(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return CompletableFuture.completedFuture(guard);
        return handle(aiService.atsScore(user, request));
    }

    @PostMapping("/cover-letter")
    public CompletableFuture<ResponseEntity<Object>> generateCoverLetter(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return CompletableFuture.completedFuture(guard);
        return handle(aiService.generateCoverLetter(user, request));
    }

    @PostMapping("/linkedin")
    public CompletableFuture<ResponseEntity<Object>> optimizeLinkedIn(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return CompletableFuture.completedFuture(guard);
        return handle(aiService.optimizeLinkedIn(user, request));
    }

    @PostMapping({"/grammar-check", "/grammar"})
    public CompletableFuture<ResponseEntity<Object>> checkGrammar(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return CompletableFuture.completedFuture(guard);
        return handle(aiService.checkGrammar(user, request));
    }

    @PostMapping("/interview-prep")
    public CompletableFuture<ResponseEntity<Object>> generateInterviewPrep(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return CompletableFuture.completedFuture(guard);
        return handle(aiService.generateInterviewPrep(user, request));
    }
}
