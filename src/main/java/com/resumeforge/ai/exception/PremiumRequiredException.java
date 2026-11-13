package com.resumeforge.ai.exception;

/**
 * AI-01 FIX: thrown when a caller tries to use a Premium-only feature without
 * an active premium subscription.
 *
 * <p>Previously {@code AiService.requirePremium} threw
 * {@link UnauthorizedException}, which the global handler maps to HTTP 401 —
 * identical to "your session is invalid". That is wrong and actively harmful:
 * the frontend's axios interceptor treats 401 as a dead session and logs the
 * user out, so a free user tapping a premium AI feature was kicked out of the
 * app instead of being shown the upgrade prompt. This dedicated exception
 * maps to HTTP 403 with errorCode {@code PREMIUM_REQUIRED}, matching what
 * {@code AiController.rejectIfNotPremium} already returned.</p>
 */
public class PremiumRequiredException extends RuntimeException {
    public PremiumRequiredException(String message) {
        super(message);
    }
}
