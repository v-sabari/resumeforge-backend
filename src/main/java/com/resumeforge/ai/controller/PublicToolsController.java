package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.ApiResponse;
import com.resumeforge.ai.dto.AtsScoreResponse;
import com.resumeforge.ai.dto.FormatCheckResponse;
import com.resumeforge.ai.exception.BadRequestException;
import com.resumeforge.ai.service.AtsToolsService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * TOOLS-01 / TOOLS-02: the two free, public ATS tools that replace the old
 * mock tools on the {@code /tools} page.
 *
 * <ul>
 *   <li>{@code POST /api/public/tools/ats-score} — resume file + pasted job
 *       description → estimated ATS compatibility score, missing keywords and
 *       per-factor breakdown.</li>
 *   <li>{@code POST /api/public/tools/format-check} — resume file → ATS
 *       format &amp; parsing report.</li>
 * </ul>
 *
 * <p>These endpoints are intentionally anonymous (the tools are advertised as
 * free) and therefore protected by per-IP rate limiting in
 * {@code SensitiveEndpointRateLimitFilter} plus Spring's multipart size limits.
 * No user account, no premium gating, and no AI quota is consumed for the
 * deterministic path.</p>
 */
@RestController
@RequestMapping("/api/public/tools")
public class PublicToolsController {

    private final AtsToolsService toolsService;

    public PublicToolsController(AtsToolsService toolsService) {
        this.toolsService = toolsService;
    }

    @PostMapping(value = "/ats-score", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse> atsScore(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "jobDescription", required = false) String jobDescription)
            throws IOException {

        requireFile(file);
        AtsScoreResponse result = toolsService.atsScore(
                file.getBytes(), file.getOriginalFilename(), file.getContentType(), jobDescription);

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("ATS score calculated.")
                .data(result)
                .build());
    }

    @PostMapping(value = "/format-check", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse> formatCheck(
            @RequestParam("file") MultipartFile file) throws IOException {

        requireFile(file);
        FormatCheckResponse result = toolsService.formatCheck(
                file.getBytes(), file.getOriginalFilename(), file.getContentType());

        return ResponseEntity.ok(ApiResponse.builder()
                .success(true)
                .message("Format check complete.")
                .data(result)
                .build());
    }

    private void requireFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Please attach a resume file (PDF, DOCX or TXT).");
        }
    }
}
