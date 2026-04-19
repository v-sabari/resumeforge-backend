package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.service.AiService;
import com.resumeforge.ai.service.CurrentUserService;
import org.springframework.web.bind.annotation.*;

/**
 * AI feature endpoints.
 *
 * All endpoints require authentication (enforced by SecurityConfig).
 * All endpoints are rate-limited (10 req/min per user) by AiRateLimitInterceptor.
 * Premium gating and daily limits are enforced inside AiService.
 *
 * Feature access matrix:
 *   /summary        — all users
 *   /bullets        — all users
 *   /skills         — all users
 *   /rewrite        — all users
 *   /grammar-check  — all users
 *   /ats-score      — free: 3/day · premium: unlimited
 *   /linkedin       — free: 1/day · premium: unlimited
 *   /cover-letter   — premium only
 *   /tailor         — premium only
 *   /interview-prep — premium only
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;
    private final CurrentUserService currentUserService;

    public AiController(AiService aiService, CurrentUserService currentUserService) {
        this.aiService          = aiService;
        this.currentUserService = currentUserService;
    }

    // ── Original features ────────────────────────────────────────────

    @PostMapping("/summary")
    public AiTextResponse summary(@RequestBody AiSummaryRequest request) {
        return aiService.generateSummary(request, currentUserService.getCurrentUser());
    }

    @PostMapping("/bullets")
    public AiListResponse bullets(@RequestBody AiBulletsRequest request) {
        return aiService.generateBullets(request, currentUserService.getCurrentUser());
    }

    @PostMapping("/skills")
    public AiListResponse skills(@RequestBody AiSkillsRequest request) {
        return aiService.suggestSkills(request, currentUserService.getCurrentUser());
    }

    @PostMapping("/rewrite")
    public AiTextResponse rewrite(@RequestBody AiRewriteRequest request) {
        return aiService.rewrite(request, currentUserService.getCurrentUser());
    }

    // ── New Phase 2 features ─────────────────────────────────────────

    /**
     * ATS compatibility score.
     * Free users: 3 per day. Premium: unlimited.
     * Returns score 0-100, matched/missing keywords, and top fixes.
     */
    @PostMapping("/ats-score")
    public AtsScoreResponse atsScore(@RequestBody AtsScoreRequest request) {
        return aiService.analyzeAtsScore(request, currentUserService.getCurrentUser());
    }

    /**
     * Cover letter generation.
     * Premium only.
     */
    @PostMapping("/cover-letter")
    public AiTextResponse coverLetter(@RequestBody CoverLetterRequest request) {
        return aiService.generateCoverLetter(request, currentUserService.getCurrentUser());
    }

    /**
     * Job-specific resume tailoring.
     * Rewrites summary and bullets to match the provided job description.
     * Premium only.
     */
    @PostMapping("/tailor")
    public AiTailorResponse tailor(@RequestBody AiTailorRequest request) {
        return aiService.tailorResume(request, currentUserService.getCurrentUser());
    }

    /**
     * LinkedIn headline and About section optimizer.
     * Free users: 1 per day. Premium: unlimited.
     */
    @PostMapping("/linkedin")
    public LinkedInResponse linkedin(@RequestBody LinkedInRequest request) {
        return aiService.optimizeLinkedIn(request, currentUserService.getCurrentUser());
    }

    /**
     * Interview preparation: 5 questions with model answers.
     * Premium only.
     */
    @PostMapping("/interview-prep")
    public InterviewPrepResponse interviewPrep(@RequestBody InterviewPrepRequest request) {
        return aiService.generateInterviewPrep(request, currentUserService.getCurrentUser());
    }

    /**
     * Grammar and clarity check.
     * Free for all users. No daily cap (covered by rate limiter).
     */
    @PostMapping("/grammar-check")
    public GrammarCheckResponse grammarCheck(@RequestBody GrammarCheckRequest request) {
        return aiService.checkGrammar(request, currentUserService.getCurrentUser());
    }
}
