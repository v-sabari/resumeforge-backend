package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.ApiResponse;
import com.resumeforge.ai.dto.FeedbackRequest;
import com.resumeforge.ai.dto.PublicFeedbackResponse;
import com.resumeforge.ai.service.FeedbackService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    @Autowired
    private FeedbackService feedbackService;

    /**
     * Submit feedback. Public endpoint (permitAll) so anonymous visitors can
     * report bugs. Rate-limited to 3 submissions / 10 min / IP by
     * FeedbackRateLimitFilter, with a honeypot field guarding against bots.
     */
    @PostMapping
    public ResponseEntity<ApiResponse> submitFeedback(
            @Valid @RequestBody FeedbackRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(feedbackService.submit(request, httpRequest));
    }

    /**
     * Public listing of general feedback only (no emails, no bug/feature rows).
     * Newest first, paginated for the "Load more" button.
     */
    @GetMapping("/public")
    public ResponseEntity<ApiResponse> getPublicFeedback(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "6") int size) {
        PublicFeedbackResponse payload = feedbackService.getPublicFeedback(page, size);
        return ResponseEntity.ok(ApiResponse.success("OK", payload));
    }
}
