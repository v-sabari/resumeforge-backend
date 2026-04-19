package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.ExportUsage;
import com.resumeforge.ai.repository.ExportUsageRepository;
import com.resumeforge.ai.service.*;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/export")
public class ExportController {

    private final ExportService       exportService;
    private final CurrentUserService  currentUserService;
    private final PdfExportService    pdfExportService;
//    private final DocxExportService   docxExportService;
    private final TxtExportService    txtExportService;
    private final ExportUsageRepository exportUsageRepository;

    public ExportController(ExportService exportService,
                            CurrentUserService currentUserService,
                            PdfExportService pdfExportService,
//                            DocxExportService docxExportService,
                            TxtExportService txtExportService,
                            ExportUsageRepository exportUsageRepository) {
        this.exportService         = exportService;
        this.currentUserService    = currentUserService;
        this.pdfExportService      = pdfExportService;
//        this.docxExportService     = docxExportService;
        this.txtExportService      = txtExportService;
        this.exportUsageRepository = exportUsageRepository;
    }

    @PostMapping("/check-access")
    public ExportAccessResponse checkAccess() {
        return exportService.checkAccess(currentUserService.getCurrentUser());
    }

    @PostMapping("/record")
    public ExportRecordResponse record(@RequestBody(required = false) ExportRecordRequest request) {
        return exportService.record(currentUserService.getCurrentUser(), request);
    }

    @GetMapping("/status")
    public ExportStatusResponse status() {
        return exportService.status(currentUserService.getCurrentUser());
    }

    /**
     * GET /api/export/history
     * Returns the authenticated user's export history (most recent first).
     * Shows export count, format, and date for each export event.
     */
    @GetMapping("/history")
    public List<ExportHistoryItem> history() {
        var user = currentUserService.getCurrentUser();
        return exportUsageRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(e -> new ExportHistoryItem(
                        e.getId(),
                        e.getExportCount(),
                        e.isAdCompleted(),
                        e.getCreatedAt()))
                .toList();
    }

    /** Download resume as PDF. */
    @GetMapping("/download/{resumeId}")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long resumeId) {
        var user     = currentUserService.getCurrentUser();
        byte[] bytes = pdfExportService.generateResumePdf(resumeId, user);
        String name  = pdfExportService.buildFilename(resumeId, user);
        return fileResponse(bytes, name, MediaType.APPLICATION_PDF_VALUE);
    }

    /** Download resume as DOCX (ATS-safe single-column, Apache POI). */
//    @GetMapping("/download/{resumeId}/docx")
//    public ResponseEntity<byte[]> downloadDocx(@PathVariable Long resumeId) {
//        var user     = currentUserService.getCurrentUser();
//        byte[] bytes = docxExportService.generateResumeDocx(resumeId, user);
//        String name  = docxExportService.buildFilename(resumeId, user);
//        return fileResponse(bytes, name,
//                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
//    }

    /**
     * Download resume as plain text (.txt).
     * Ideal for pasting into ATS portals that only accept text input.
     */
    @GetMapping("/download/{resumeId}/txt")
    public ResponseEntity<byte[]> downloadTxt(@PathVariable Long resumeId) {
        var user     = currentUserService.getCurrentUser();
        byte[] bytes = txtExportService.generateResumeTxt(resumeId, user);
        String name  = txtExportService.buildFilename(resumeId, user);
        return fileResponse(bytes, name, "text/plain; charset=UTF-8");
    }

    private ResponseEntity<byte[]> fileResponse(byte[] bytes, String filename, String mimeType) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, mimeType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(bytes);
    }

    /** DTO for export history list */
    public record ExportHistoryItem(
            Long id,
            int exportCount,
            boolean adCompleted,
            java.time.Instant createdAt
    ) {}
}
