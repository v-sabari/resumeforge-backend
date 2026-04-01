package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.service.CurrentUserService;
import com.resumeforge.ai.service.ExportService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/export")
public class ExportController {
    private final ExportService exportService;
    private final CurrentUserService currentUserService;

    public ExportController(ExportService exportService, CurrentUserService currentUserService) {
        this.exportService = exportService;
        this.currentUserService = currentUserService;
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
}