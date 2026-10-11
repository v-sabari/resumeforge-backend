package com.resumeforge.ai.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Payload for POST /api/feedback.
 *
 * Server-side validation mirrors the frontend rules AND is the source of
 * truth: allowed types only, rating 1-5, title <= 120, details <= 1000.
 * {@code userName}/{@code userEmail} are optional and only populated when the
 * visitor is signed in. {@code website} is a honeypot that must stay empty.
 */
@Data
public class FeedbackRequest {

    @NotBlank(message = "Feedback type is required")
    @Pattern(regexp = "bug|feature|general", message = "Type must be bug, feature or general")
    private String type;

    @NotNull(message = "Rating is required")
    @Min(value = 1, message = "Rating must be between 1 and 5")
    @Max(value = 5, message = "Rating must be between 1 and 5")
    private Integer rating;

    @NotBlank(message = "Title is required")
    @Size(max = 120, message = "Title must be at most 120 characters")
    private String title;

    @NotBlank(message = "Details are required")
    @Size(max = 1000, message = "Details must be at most 1000 characters")
    private String details;

    @Size(max = 500, message = "pageUrl is too long")
    private String pageUrl;

    @Size(max = 500, message = "userAgent is too long")
    private String userAgent;

    @Size(max = 120, message = "userName is too long")
    private String userName;

    @Email(message = "Invalid email format")
    @Size(max = 160, message = "userEmail is too long")
    private String userEmail;

    /**
     * Honeypot. Hidden in the UI; real users never fill it. A non-empty value
     * is treated as spam and silently accepted (no persistence, no email).
     */
    private String website;
}
