package com.resumeforge.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI-01 FIX: Added {@code errorCode} field so AI failures return structured
 * JSON with a machine-readable code:
 *   { "success": false, "message": "...", "errorCode": "OPENROUTER_AUTH_ERROR" }
 *
 * The field is null-excluded from serialization on success responses so
 * existing consumers that only check { success, message, data } are unaffected.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse {
    private boolean success;
    private String message;
    private Object data;

    /**
     * Machine-readable error code — only present on error responses.
     * Maps to {@link com.resumeforge.ai.exception.AiException.ErrorCode} names
     * or any other string code set by callers.
     */
    private String errorCode;

    // ── Success factories ─────────────────────────────────────────────────────

    public static ApiResponse success(String message) {
        return ApiResponse.builder()
                .success(true)
                .message(message)
                .build();
    }

    public static ApiResponse success(String message, Object data) {
        return ApiResponse.builder()
                .success(true)
                .message(message)
                .data(data)
                .build();
    }

    // ── Error factories ───────────────────────────────────────────────────────

    public static ApiResponse error(String message) {
        return ApiResponse.builder()
                .success(false)
                .message(message)
                .build();
    }

    /**
     * AI-01 FIX: error factory that includes a machine-readable errorCode.
     * Used by GlobalExceptionHandler for AiException cases.
     */
    public static ApiResponse error(String message, String errorCode) {
        return ApiResponse.builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .build();
    }
}