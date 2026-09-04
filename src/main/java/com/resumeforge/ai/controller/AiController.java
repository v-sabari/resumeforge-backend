package com.resumeforge.ai.controller;

import tools.jackson.databind.JsonNode;
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

/**
 * AI-01 FIX — AiController improvements:
 *
 * 1. Null-user guard on every endpoint: if the JWT filter failed silently and
 *    injected null for @AuthenticationPrincipal, we now return 401 immediately
 *    instead of NPE → 500.
 *
 * 2. handle() correctly extracts AiException.ErrorCode so the structured
 *    errorCode field reaches the client instead of a generic 500.
 *
 * AI-02 FIX:
 * 3. handle() takes the parsed {@link JsonNode} straight from AiService
 *    (which no longer wraps the model's structured output in the old flat
 *    {result, inputTokens, outputTokens} DTO) and returns it directly.
 *    Spring's default Jackson message converter serializes a JsonNode
 *    natively, so the client receives exactly the shape aiService.js /
 *    AIActionPanel.jsx expect.
 *
 * AI-03 FIX — synchronous conversion:
 * 4. Every endpoint here used to be @Async + CompletableFuture, which meant
 *    each request needed a *second* Spring Security authorization check on
 *    the follow-up "async dispatch" that finally wrote the response. Live
 *    testing showed genuine, correctly-authenticated requests occasionally
 *    coming back a hard 401 on exactly these endpoints — and only these
 *    endpoints; every synchronous controller in this app has been
 *    completely reliable. AiService's methods are now plain synchronous
 *    calls, so these controller methods are too: one dispatch, one
 *    authorization check, on the thread that was already authenticated
 *    when the request came in. No second dispatch means no race for
 *    Spring Security's context propagation to lose.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    /**
     * Runs the given AiService call and maps any exception it throws to the
     * correct HTTP status + structured ApiResponse body. Synchronous now —
     * no CompletableFuture, no second dispatch.
     */
    private ResponseEntity<Object> handle(java.util.function.Supplier<JsonNode> call) {
        try {
            return ResponseEntity.ok((Object) call.get());

        } catch (AiException aie) {
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

        } catch (RateLimitException rle) {
            return ResponseEntity
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .<Object>body(ApiResponse.error(rle.getMessage()));

        } catch (Exception ex) {
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .<Object>body(ApiResponse.error(
                            ex.getMessage() != null ? ex.getMessage() : "AI service error"));
        }
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
    public ResponseEntity<Object> rewrite(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return guard;
        return handle(() -> aiService.rewriteContent(user, request));
    }

    @PostMapping("/bullets")
    public ResponseEntity<Object> improveBullets(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return guard;
        return handle(() -> aiService.improveBullets(user, request));
    }

    @PostMapping("/summary")
    public ResponseEntity<Object> generateSummary(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return guard;
        return handle(() -> aiService.generateSummary(user, request));
    }

    @PostMapping("/skills")
    public ResponseEntity<Object> extractSkills(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return guard;
        return handle(() -> aiService.extractSkills(user, request));
    }

    @PostMapping("/tailor")
    public ResponseEntity<Object> tailorToJob(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return guard;
        return handle(() -> aiService.tailorToJob(user, request));
    }

    @PostMapping("/ats-score")
    public ResponseEntity<Object> atsScore(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return guard;
        return handle(() -> aiService.atsScore(user, request));
    }

    @PostMapping("/cover-letter")
    public ResponseEntity<Object> generateCoverLetter(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return guard;
        return handle(() -> aiService.generateCoverLetter(user, request));
    }

    @PostMapping("/linkedin")
    public ResponseEntity<Object> optimizeLinkedIn(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return guard;
        return handle(() -> aiService.optimizeLinkedIn(user, request));
    }

    @PostMapping({"/grammar-check", "/grammar"})
    public ResponseEntity<Object> checkGrammar(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return guard;
        return handle(() -> aiService.checkGrammar(user, request));
    }

    @PostMapping("/interview-prep")
    public ResponseEntity<Object> generateInterviewPrep(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return guard;
        return handle(() -> aiService.generateInterviewPrep(user, request));
    }

    /**
     * CHAT-01: Premium-only conversational resume builder — single turn.
     * Rejects Free users with 403 BEFORE any AI spend.
     */
    @PostMapping("/chat")
    public ResponseEntity<Object> chat(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return guard;
        ResponseEntity<Object> prem = rejectIfNotPremium(user);
        if (prem != null) return prem;
        return handle(() -> aiService.chatWithAI(user, request));
    }

    /**
     * CHAT-01: Premium-only resume generation from conversation context.
     */
    @PostMapping("/chat/generate")
    public ResponseEntity<Object> generateResumeFromChat(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody AiRequest request) {
        ResponseEntity<Object> guard = rejectIfUnauthenticated(user);
        if (guard != null) return guard;
        ResponseEntity<Object> prem = rejectIfNotPremium(user);
        if (prem != null) return prem;
        return handle(() -> aiService.generateResumeFromChat(user, request));
    }

    private ResponseEntity<Object> rejectIfNotPremium(User user) {
        if (user == null || !user.isPremium()) {
            return ResponseEntity
                    .status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(
                            "The AI Resume Builder is a Premium feature. Please upgrade to Premium to build your resume through AI conversation.",
                            "PREMIUM_REQUIRED"));
        }
        return null;
    }
}
