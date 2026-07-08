package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.BadRequestException;
import com.resumeforge.ai.exception.ResourceNotFoundException;
import com.resumeforge.ai.service.ResumeService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RES-01 FIX — ResumeController changes:
 *
 * PROBLEM: PUT /{id} had no try/catch — any RuntimeException from the service
 * layer (including re-thrown DataIntegrityViolationException) fell through to
 * GlobalExceptionHandler.handleGenericException() → HTTP 500 with no useful
 * body. The frontend had no way to distinguish a validation failure from a
 * genuine server crash.
 *
 * FIX: updateResume() now catches BadRequestException and ResourceNotFoundException
 * explicitly and returns proper 400 / 404 responses with the error message from
 * ResumeService. All other exceptions still bubble to GlobalExceptionHandler.
 *
 * NOTE: @Valid on the @RequestBody is intentionally kept — it runs the
 * @Pattern / @Size / @NotBlank constraints on ResumeRequest BEFORE the method
 * body executes, returning HTTP 400 with field-level detail via
 * GlobalExceptionHandler.handleValidationErrors(). ResumeService.resolveTemplate()
 * is a second, independent guard for values that somehow bypass DTO validation.
 */
@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private static final Logger log = LoggerFactory.getLogger(ResumeController.class);

    @Autowired
    private ResumeService resumeService;

    @PostMapping
    public ResponseEntity<ResumeResponse> createResume(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ResumeRequest request) {
        return ResponseEntity.ok(resumeService.createResume(user, request));
    }

    @GetMapping
    public ResponseEntity<List<ResumeResponse>> getAllResumes(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(resumeService.getAllResumes(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResumeResponse> getResume(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(resumeService.getResume(user, id));
    }

    /**
     * RES-01 FIX: PUT /{id} now surfaces service-layer validation errors as
     * HTTP 400 / 404 instead of silently becoming HTTP 500.
     *
     * Flow:
     *   1. @Valid fires → MethodArgumentNotValidException → GlobalExceptionHandler
     *      returns 400 with field errors (unchanged behaviour, works correctly).
     *   2. Service-layer BadRequestException (invalid template after whitelist,
     *      DB constraint violation, oversized field) → caught here → 400.
     *   3. ResourceNotFoundException (resume not owned by user) → caught here → 404.
     *   4. Any other exception → bubbles to GlobalExceptionHandler → 500 with
     *      full stack trace now logged by ResumeService.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateResume(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody ResumeRequest request) {

        try {
            ResumeResponse response = resumeService.updateResume(user, id, request);
            return ResponseEntity.ok(response);

        } catch (ResourceNotFoundException ex) {
            log.warn("updateResume NOT_FOUND resumeId={} userId={}", id, user.getId());
            return ResponseEntity.status(404)
                    .body(ApiResponse.error(ex.getMessage()));

        } catch (BadRequestException ex) {
            log.warn("updateResume BAD_REQUEST resumeId={} userId={} reason='{}'",
                    id, user.getId(), ex.getMessage());
            return ResponseEntity.status(400)
                    .body(ApiResponse.error(ex.getMessage()));
        }
        // All other exceptions intentionally not caught here —
        // they propagate to GlobalExceptionHandler which returns 500.
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteResume(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        resumeService.deleteResume(user, id);
        return ResponseEntity.ok(ApiResponse.success("Resume deleted successfully"));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<List<SnapshotResponse>> getHistory(
            @AuthenticationPrincipal User user,
            @PathVariable Long id) {
        return ResponseEntity.ok(resumeService.getResumeHistory(user, id));
    }

    @PostMapping("/{id}/history/{snapshotId}/restore")
    public ResponseEntity<ResumeResponse> restoreSnapshot(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @PathVariable Long snapshotId) {
        return ResponseEntity.ok(resumeService.restoreSnapshot(user, id, snapshotId));
    }
}
