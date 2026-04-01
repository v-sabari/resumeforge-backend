package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.ExportAccessResponse;
import com.resumeforge.ai.dto.ExportRecordRequest;
import com.resumeforge.ai.dto.ExportRecordResponse;
import com.resumeforge.ai.dto.ExportStatusResponse;
import com.resumeforge.ai.service.CurrentUserService;
import com.resumeforge.ai.service.ExportService;
import com.resumeforge.ai.service.PdfExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/export")
public class ExportController {
    private final ExportService exportService;
    private final CurrentUserService currentUserService;
    private final PdfExportService pdfExportService;

    public ExportController(
            ExportService exportService,
            CurrentUserService currentUserService,
            PdfExportService pdfExportService
    ) {
        this.exportService = exportService;
        this.currentUserService = currentUserService;
        this.pdfExportService = pdfExportService;
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

    @GetMapping("/download/{resumeId}")
    public ResponseEntity<byte[]> download(@PathVariable Long resumeId) {
        byte[] pdfBytes = pdfExportService.generateResumePdf(resumeId, currentUserService.getCurrentUser());
        String filename = pdfExportService.buildFilename(resumeId, currentUserService.getCurrentUser());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                ContentDisposition.attachment().filename(filename).build()
        );

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
}