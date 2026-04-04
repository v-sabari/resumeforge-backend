package com.cvcraft.ai.controller;
import com.cvcraft.ai.dto.*;
import com.cvcraft.ai.service.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/export")
public class ExportController {
    private final ExportService exportService;
    private final CurrentUserService currentUserService;
    private final PdfExportService pdfExportService;
    public ExportController(ExportService es, CurrentUserService cs, PdfExportService ps) {
        this.exportService = es; this.currentUserService = cs; this.pdfExportService = ps;
    }
    @PostMapping("/check-access") public ExportAccessResponse checkAccess() { return exportService.checkAccess(currentUserService.getCurrentUser()); }
    @PostMapping("/record")       public ExportRecordResponse record(@RequestBody(required = false) ExportRecordRequest req) { return exportService.record(currentUserService.getCurrentUser(), req); }
    @GetMapping("/status")        public ExportStatusResponse status() { return exportService.status(currentUserService.getCurrentUser()); }
    @GetMapping("/download/{resumeId}")
    public ResponseEntity<byte[]> download(@PathVariable Long resumeId) {
        var user     = currentUserService.getCurrentUser();
        byte[] bytes = pdfExportService.generateResumePdf(resumeId, user);
        String name  = pdfExportService.buildFilename(resumeId, user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(bytes);
    }
}
