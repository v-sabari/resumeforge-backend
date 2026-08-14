package com.resumeforge.ai.exception;

import com.resumeforge.ai.dto.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RES-01 FIX — GlobalExceptionHandler additions:
 *
 * PROBLEM 1: DataIntegrityViolationException (DB constraint violations — NOT NULL,
 * length overflow, FK failure) was caught only by handleGenericException() → HTTP 500
 * with "An unexpected error occurred". No indication it was a data problem.
 *
 * PROBLEM 2: ConstraintViolationException (Hibernate bean-validation at flush time)
 * same problem — fell through to the generic 500 handler.
 *
 * PROBLEM 3: handleGenericException() called ex.printStackTrace() — this dumps to
 * stdout/stderr without a logger, which gets swallowed under Render log aggregation
 * at high throughput. Changed to log.error() with the full exception so it appears
 * in structured Render logs with timestamp and level.
 *
 * FIX:
 *   - Added handleDataIntegrityViolation() → HTTP 422 Unprocessable Entity.
 *     HTTP 422 is correct here: the request was syntactically valid JSON (so not 400)
 *     but the data values violated a DB constraint (so not 500 — it is the caller's
 *     data that is the problem).
 *   - Added handleConstraintViolation() → HTTP 400 Bad Request.
 *     Bean-validation constraint violations are a client data problem.
 *   - handleGenericException() now uses log.error() instead of ex.printStackTrace().
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse> handleBadRequest(BadRequestException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Invalid email or password"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Authentication failed"));
    }

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiResponse> handleRateLimit(RateLimitException ex) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.error(ex.getMessage()));
    }

    /**
     * AI-01 FIX (unchanged): Handle typed AI exceptions.
     */
    @ExceptionHandler(AiException.class)
    public ResponseEntity<ApiResponse> handleAiException(AiException ex) {
        HttpStatus status = switch (ex.getErrorCode()) {
            case OPENROUTER_AUTH_ERROR     -> HttpStatus.BAD_GATEWAY;
            case OPENROUTER_FORBIDDEN      -> HttpStatus.BAD_GATEWAY;
            case OPENROUTER_RATE_LIMIT     -> HttpStatus.TOO_MANY_REQUESTS;
            case OPENROUTER_UNAVAILABLE    -> HttpStatus.SERVICE_UNAVAILABLE;
            case OPENROUTER_EMPTY_RESPONSE -> HttpStatus.BAD_GATEWAY;
            case AI_SERVICE_ERROR          -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity
                .status(status)
                .body(ApiResponse.error(ex.getMessage(), ex.getErrorCode().name()));
    }

    /**
     * RES-01 FIX: DB constraint violations.
     *
     * Thrown by Spring's JDBC layer when a SQL statement violates a NOT NULL,
     * UNIQUE, CHECK, or FK constraint. After ResumeService sanitization this
     * should never fire for normal resume updates — but it is caught here as a
     * safety net so any remaining edge case returns HTTP 422 instead of 500.
     *
     * HTTP 422 (Unprocessable Entity) is correct: the request body was valid JSON
     * but the contained data values conflict with the DB schema.
     *
     * We log at WARN (not ERROR) because this is a data problem, not a server
     * crash. The root cause message is logged with full detail server-side for
     * diagnostics, but the response body deliberately does NOT echo it — the raw
     * DB message (constraint names, table/column details) leaks schema internals
     * to clients.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        String rootMsg = rootCause(ex);
        log.warn("DataIntegrityViolationException: {}", rootMsg);
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(ApiResponse.error(
                        "The request data conflicts with existing constraints. " +
                        "Check required fields and value lengths."));
    }

    /**
     * RES-01 FIX: Hibernate bean-validation constraint violations at flush time.
     *
     * Thrown when a JPA entity field fails a Jakarta Validation annotation
     * (@NotNull, @Size, @Pattern, etc.) during the Hibernate flush before
     * the actual SQL is issued. These are client data problems → HTTP 400.
     *
     * Each violation's property path and message is included in the response
     * so the caller knows exactly which field failed.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse> handleConstraintViolation(
            ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .collect(Collectors.joining("; "));
        log.warn("ConstraintViolationException: {}", detail);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Validation failed: " + detail));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName    = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Validation failed");
        response.put("errors", errors);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(response);
    }

    /**
     * RES-01 FIX: Changed ex.printStackTrace() → log.error().
     *
     * printStackTrace() writes to stderr without a logger — on Render this
     * appears as unstructured text outside the normal log stream and is
     * dropped under high concurrency. log.error() with the exception argument
     * emits a structured record with timestamp, thread, level, and full stack
     * trace, all visible in the Render log dashboard.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("An unexpected error occurred"));
    }

    /** Walk the cause chain to find the most specific error message. */
    private String rootCause(Throwable ex) {
        Throwable cause = ex;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage() != null ? cause.getMessage() : ex.getMessage();
    }
}
