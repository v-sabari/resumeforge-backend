package com.resumeforge.ai.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Production-hardened global exception handler.
 *
 * Security requirements:
 *   1. Never expose stack traces or internal class names to clients.
 *   2. Never expose raw exception messages from unexpected errors.
 *   3. Log ALL unexpected errors server-side with a correlation request ID.
 *   4. Return structured, consistent JSON for all error types.
 *
 * Error response shape:
 *   { timestamp, status, message, requestId }
 *   For validation: also includes { errors: { field: message } }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ── Application-defined API errors (safe to show message) ─────────

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApiException(ApiException ex,
                                                                   HttpServletRequest req) {
        // ApiException messages are written by us and are safe for clients.
        return build(ex.getStatus(), ex.getMessage(), null, req);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex,
                                                               HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), null, req);
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedException ex,
                                                                   HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), null, req);
    }

    // ── Bean validation ────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex,
                                                                 HttpServletRequest req) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        Map<String, Object> body = buildBody(HttpStatus.BAD_REQUEST, "Validation failed", req);
        body.put("errors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex,
                                                                   HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '" + ex.getName() + "'", null, req);
    }

    // ── Spring Security ────────────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex,
                                                                   HttpServletRequest req) {
        // Don't expose the exact reason — just say forbidden.
        return build(HttpStatus.FORBIDDEN, "Access denied.", null, req);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(BadCredentialsException ex,
                                                                      HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, "Invalid credentials.", null, req);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<Map<String, Object>> handleDisabled(DisabledException ex,
                                                               HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED,
                "Account is not enabled. Please verify your email.", null, req);
    }

    // ── HTTP protocol errors ───────────────────────────────────────────

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED,
                "HTTP method not allowed for this endpoint.", null, req);
    }

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandler(NoHandlerFoundException ex,
                                                                HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "Endpoint not found.", null, req);
    }

    // ── Catch-all — NEVER expose internal details ──────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex,
                                                                 HttpServletRequest req) {
        // Generate a correlation ID so we can link client-reported errors to server logs.
        String requestId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        // Log the full stack trace server-side only.
        log.error("Unexpected error [requestId={}] on {} {}: {}",
                requestId,
                req.getMethod(),
                req.getRequestURI(),
                ex.getMessage(),
                ex);

        // Never expose the raw exception message — it may contain SQL, paths, or credentials.
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Reference: " + requestId,
                requestId,
                req);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message,
                                                       String requestId, HttpServletRequest req) {
        return ResponseEntity.status(status).body(buildBody(status, message, req, requestId));
    }

    private Map<String, Object> buildBody(HttpStatus status, String message, HttpServletRequest req) {
        return buildBody(status, message, req, null);
    }

    private Map<String, Object> buildBody(HttpStatus status, String message,
                                           HttpServletRequest req, String requestId) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("message", message != null ? message : "An error occurred.");
        if (requestId != null) body.put("requestId", requestId);
        return body;
    }
}
