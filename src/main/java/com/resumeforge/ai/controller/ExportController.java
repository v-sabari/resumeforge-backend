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

@RestController
@RequestMapping("/api/export")
public class ExportController {

    @Autowired
    private ExportService exportService;

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

    @GetMapping("/download/{resumeId}")
    public ResponseEntity<byte[]> downloadPdf(
            @AuthenticationPrincipal User user,
            @PathVariable Long resumeId) {
        byte[] pdf = exportService.exportToPdf(user, resumeId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resume.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/download/{resumeId}/docx")
    public ResponseEntity<byte[]> downloadDocx(
            @AuthenticationPrincipal User user,
            @PathVariable Long resumeId) {
        byte[] docx = exportService.exportToDocx(user, resumeId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resume.docx")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(docx);
    }

    @GetMapping("/download/{resumeId}/txt")
    public ResponseEntity<byte[]> downloadTxt(
            @AuthenticationPrincipal User user,
            @PathVariable Long resumeId) {
        byte[] txt = exportService.exportToTxt(user, resumeId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=resume.txt")
                .contentType(MediaType.TEXT_PLAIN)
                .body(txt);
    }
}
