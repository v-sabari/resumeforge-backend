package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.ApiResponse;
import com.resumeforge.ai.dto.FeedbackRequest;
import com.resumeforge.ai.dto.PublicFeedbackDto;
import com.resumeforge.ai.dto.PublicFeedbackResponse;
import com.resumeforge.ai.entity.Feedback;
import com.resumeforge.ai.exception.BadRequestException;
import com.resumeforge.ai.repository.FeedbackRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Feedback system (bug reports, feature requests, general feedback).
 *
 * Behaviour by type:
 *   bug / feature : persisted with status "new" and emailed to the operator.
 *                   NEVER exposed publicly.
 *   general       : persisted, emailed, AND returned by GET /api/feedback/public.
 *
 * Email is best-effort: a provider failure is logged and surfaced as a warning
 * in the response but the feedback is still saved, because Render's filesystem
 * is ephemeral and losing a submission is worse than a failed notification.
 */
@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private static final Set<String> ALLOWED_TYPES = Set.of("bug", "feature", "general");
    private static final String PUBLIC_TYPE = "general";

    private static final int MAX_TITLE     = 120;
    private static final int MAX_DETAILS   = 1000;
    private static final int MAX_URL       = 500;
    private static final int MAX_USER_AGENT = 500;
    private static final int MAX_NAME      = 120;
    private static final int MAX_EMAIL     = 160;
    private static final int MAX_PAGE_SIZE = 24;

    @Autowired
    private FeedbackRepository feedbackRepository;

    @Autowired
    private EmailService emailService;

    // ------------------------------------------------------------------
    // Submit
    // -------------------------------------------------------------------------

    @Transactional
    public ApiResponse submit(FeedbackRequest request, HttpServletRequest http) {

        // --- Honeypot ------------------------------------------------
        // Real users never fill the hidden "website" field. Return the exact
        // same success shape as a normal submission so bots get no signal,
        // but drop the payload (no persistence, no email, no rate-limit cost).
        if (request.getWebsite() != null && !request.getWebsite().isBlank()) {
            log.warn("Feedback honeypot triggered ip={} ua={}",
                    resolveIp(http), http.getHeader("User-Agent"));
            return ApiResponse.success("Thank you for your feedback!");
        }

        // ── Server-side validation (defence in depth: DTO annotations also run) ──
        String type = request.getType() == null ? "" : request.getType().trim().toLowerCase();
        if (!ALLOWED_TYPES.contains(type)) {
            throw new BadRequestException("Invalid feedback type");
        }

        Integer ratingIn = request.getRating();
        if (ratingIn == null || ratingIn < 1 || ratingIn > 5) {
            throw new BadRequestException("Rating must be between 1 and 5");
        }
        int rating = ratingIn;

        String title = sanitize(request.getTitle(), 120);
        if (title == null || title.isBlank()) {
            throw new BadRequestException("Title is required");
        }

        String details = sanitize(request.getDetails(), 1000);
        if (details == null || details.isBlank()) {
            throw new BadRequestException("Details are required");
        }

        // pageUrl / userAgent: prefer the client-supplied values (the SPA knows
        // the SPA route), fall back to the raw request headers.
        String pageUrl = sanitize(request.getPageUrl(), 500);
        if (pageUrl == null || pageUrl.isBlank()) {
            pageUrl = sanitize(http.getHeader("Referer"), 500);
        }
        String userAgent = sanitize(request.getUserAgent(), 500);
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = sanitize(http.getHeader("User-Agent"), 500);
        }

        String userName  = sanitize(request.getUserName(), 120);
        String userEmail = sanitizeEmail(request.getUserEmail());

        Feedback feedback = Feedback.builder()
                .type(type)
                .rating(rating)
                .title(title)
                .details(details)
                .userName(userName)
                .userEmail(userEmail)
                .pageUrl(pageUrl)
                .userAgent(userAgent)
                .status("new")
                .build();

        Feedback saved = feedbackRepository.save(feedback);

        // ── Email notification (best effort) ─────────────────────────────────
        boolean emailSent = emailService.sendFeedbackNotification(
                type, rating, title, details, userName, userEmail,
                pageUrl, userAgent, saved.getCreatedAt());

        if (!emailSent) {
            log.error("Feedback #{} ({}) was saved but the notification email could not be sent.",
                    saved.getId(), type);
        }

        // General feedback is echoed back so the UI can prepend it without a reload.
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("emailSent", emailSent);
        data.put("feedback", "general".equals(type) ? toPublicDto(saved) : null);

        String message = emailSent
                ? "Thank you for your feedback!"
                : "Your feedback was saved, but we couldn't send a notification email.";

        return ApiResponse.success(message, data);
    }

    // ── Public listing ───────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PublicFeedbackResponse getPublicFeedback(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 24);

        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<Feedback> result = feedbackRepository.findByTypeOrderByCreatedAtDesc("general", pageable);

        Double avg = feedbackRepository.averageRatingByType("general");
        double averageRating = avg == null ? 0.0 : Math.round(avg * 10.0) / 10.0;

        return new PublicFeedbackResponse(
                result.getContent().stream().map(this::toPublicDto).toList(),
                safePage,
                safeSize,
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext(),
                result.getTotalElements(),
                averageRating
        );
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private PublicFeedbackDto toPublicDto(Feedback f) {
        return new PublicFeedbackDto(
                f.getId(),
                f.getTitle(),
                f.getDetails(),
                f.getRating(),
                firstNameOrAnonymous(f.getUserName()),
                f.getCreatedAt() == null ? null : f.getCreatedAt().toInstant(ZoneOffset.UTC)
        );
    }

    /** First name only, or "Anonymous" — public listings must never expose a full identity. */
    private String firstNameOrAnonymous(String name) {
        if (name == null || name.isBlank()) return "Anonymous";
        String trimmed = name.trim();
        int space = trimmed.indexOf(' ');
        String first = space > 0 ? trimmed.substring(0, space) : trimmed;
        return first.length() > 60 ? first.substring(0, 60) : first;
    }

    /**
     * Sanitise free text before it is stored or emailed:
     *   1. strip HTML tags (kills stored-XSS payloads like <script>…</script>)
     *   2. drop control characters (except newline / carriage return / tab)
     *   3. trim and hard-truncate to the column limit
     *
     * Combined with JPA's parameterised queries (no SQL injection) and the
     * HTML escaping done when building the email, this closes the XSS and
     * injection vectors called out in the requirements.
     */
    private String sanitize(String value, int maxLen) {
        if (value == null) return null;
        String cleaned = value
                .replaceAll("<[^>]*>", " ")
                .replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "")
                .trim();
        return cleaned.length() > maxLen ? cleaned.substring(0, maxLen) : cleaned;
    }

    /** Loose email sanitisation: trim, lowercase, cap length, drop embedded CR/LF. */
    private String sanitizeEmail(String email) {
        if (email == null || email.isBlank()) return null;
        String cleaned = email.replaceAll("[\\r\\n]", "").trim().toLowerCase();
        return cleaned.length() > 160 ? cleaned.substring(0, 160) : cleaned;
    }

    private String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
