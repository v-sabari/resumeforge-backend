package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.service.ResumeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

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

    @PutMapping("/{id}")
    public ResponseEntity<ResumeResponse> updateResume(
            @AuthenticationPrincipal User user,
            @PathVariable Long id,
            @Valid @RequestBody ResumeRequest request) {
        return ResponseEntity.ok(resumeService.updateResume(user, id, request));
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
