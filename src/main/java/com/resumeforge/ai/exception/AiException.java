package com.resumeforge.ai.exception;

/**
 * AI-01 FIX — Typed exception for all OpenRouter failures.
 *
 * BEFORE: callOpenRouter() caught every exception and rethrew a bare
 *   RuntimeException("AI service error: " + e.getMessage())
 * This meant:
 *   - 401 Unauthorized from OpenRouter → 500 to the client
 *   - 403 Forbidden from OpenRouter   → 500 to the client
 *   - 429 Too Many Requests           → 500 to the client
 *   - Timeout                         → 500 to the client
 *   - All errors looked identical from the client side
 *
 * AFTER: specific error codes are preserved so GlobalExceptionHandler
 * can map them to the correct HTTP status code and return a structured
 * JSON body with { success, message, errorCode } as required by AI-01.
 */
public class AiException extends RuntimeException {

    /** Machine-readable error codes consumed by GlobalExceptionHandler */
    public enum ErrorCode {
        /** OpenRouter returned 401 — API key is wrong or revoked */
        OPENROUTER_AUTH_ERROR,
        /** OpenRouter returned 403 — model access denied or account issue */
        OPENROUTER_FORBIDDEN,
        /** OpenRouter returned 429 — upstream rate limit (different from our per-user limit) */
        OPENROUTER_RATE_LIMIT,
        /** OpenRouter returned 5xx or the network call timed out */
        OPENROUTER_UNAVAILABLE,
        /** OpenRouter returned a 2xx body but the content was null or empty */
        OPENROUTER_EMPTY_RESPONSE,
        /** Any other unexpected failure */
        AI_SERVICE_ERROR,
    }

    private final ErrorCode errorCode;

    public AiException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AiException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}