package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.service.ExportService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * ExportController — handles all resume download and export management endpoints.
 *
 * BUG-05 FIX: DOCX Content-Type changed from APPLICATION_OCTET_STREAM to the
 *             correct OOXML MIME type so browsers open/save it as a Word document.
 * BUG-08 FIX: Content-Length header added to all download responses so the
 *             browser can show download progress.
 * BUG-10 FIX: Filename is now derived from the resume title (sanitized) instead
 *             of the hardcoded "resume.pdf / resume.docx / resume.txt".
 */
@RestController
@RequestMapping("/api/export")
public class ExportController {

    // Correct OOXML MIME type for .docx files
    private static final String DOCX_MIME =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Autowired
    private ExportService exportService;

    // ── Access management ──────────────────────────────────────────────

    @PostMapping("/check-access")
    public ResponseEntity<ExportStatusResponse> checkAccess(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(exportService.checkExportAccess(user));
    }

    @PostMapping("/record")
    public ResponseEntity<ApiResponse> recordExport(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ExportRecordRequest request) {
        return ResponseEntity.ok(exportService.recordExport(user, request));
    }

    @GetMapping("/history")
    public ResponseEntity<List<ExportHistoryResponse>> getHistory(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(exportService.getExportHistory(user));
    }

    @GetMapping("/status")
    public ResponseEntity<ExportStatusResponse> getStatus(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(exportService.checkExportAccess(user));
    }

    // ── Download endpoints ─────────────────────────────────────────────

    /**
     * GET /api/export/download/{resumeId}
     * Returns the resume as a PDF byte stream.
     *
     * BUG-08 FIX: Content-Length added.
     * BUG-10 FIX: Filename derived from resume title.
     */
    @GetMapping("/download/{resumeId}")
    public ResponseEntity<byte[]> downloadPdf(
            @AuthenticationPrincipal User user,
            @PathVariable Long resumeId) {

        byte[] pdf      = exportService.exportToPdf(user, resumeId);
        String filename = exportService.safeFilename(user, resumeId) + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(pdf.length))
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /**
     * GET /api/export/download/{resumeId}/docx
     * Returns the resume as a DOCX byte stream.
     *
     * BUG-05 FIX: Content-Type is now the correct OOXML MIME type.
     * BUG-08 FIX: Content-Length added.
     * BUG-10 FIX: Filename derived from resume title.
     */
    @GetMapping("/download/{resumeId}/docx")
    public ResponseEntity<byte[]> downloadDocx(
            @AuthenticationPrincipal User user,
            @PathVariable Long resumeId) {

        byte[] docx     = exportService.exportToDocx(user, resumeId);
        String filename = exportService.safeFilename(user, resumeId) + ".docx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(docx.length))
                .contentType(MediaType.parseMediaType(DOCX_MIME))
                .body(docx);
    }

    /**
     * GET /api/export/download/{resumeId}/txt
     * Returns the resume as a plain-text byte stream (UTF-8).
     *
     * BUG-08 FIX: Content-Length added.
     * BUG-10 FIX: Filename derived from resume title.
     */
    @GetMapping("/download/{resumeId}/txt")
    public ResponseEntity<byte[]> downloadTxt(
            @AuthenticationPrincipal User user,
            @PathVariable Long resumeId) {

        byte[] txt      = exportService.exportToTxt(user, resumeId);
        String filename = exportService.safeFilename(user, resumeId) + ".txt";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(txt.length))
                .contentType(new MediaType("text", "plain",
                        java.nio.charset.StandardCharsets.UTF_8))
                .body(txt);
    }
}
