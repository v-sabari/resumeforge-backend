package com.resumeforge.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeforge.ai.dto.ApiResponse;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.repository.ExportHistoryRepository;
import com.resumeforge.ai.repository.PaymentRepository;
import com.resumeforge.ai.repository.ResumeRepository;
import com.resumeforge.ai.repository.ResumeSnapshotRepository;
import com.resumeforge.ai.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * DATA-01 FIX: GET  /api/account/export — GDPR data download.
 *   ProfilePage calls this endpoint; it previously returned 404/500 because no
 *   handler existed. Returns a JSON blob of all user data as a downloadable file.
 *
 * DATA-02 FIX: DELETE /api/account — account + all data deletion.
 *   ProfilePage calls this endpoint; same issue — no handler existed.
 *   Cascade-deletes: snapshots → resumes → export history → payments → user.
 */
@RestController
@RequestMapping("/api/account")
public class UserController {

    @Autowired private UserRepository            userRepository;
    @Autowired private ResumeRepository          resumeRepository;
    @Autowired private ResumeSnapshotRepository  snapshotRepository;
    @Autowired private ExportHistoryRepository   exportHistoryRepository;
    @Autowired private PaymentRepository         paymentRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // DATA-01: GDPR data export
    // -------------------------------------------------------------------------

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportAccountData(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("exportedAt", Instant.now().toString());

            // User profile (exclude password hash)
            Map<String, Object> profile = new LinkedHashMap<>();
            profile.put("id",            user.getId());
            profile.put("name",          user.getName());
            profile.put("email",         user.getEmail());
            profile.put("role",          user.getRole());
            profile.put("premium",       user.isPremium());
            profile.put("emailVerified", user.isEmailVerified());
            profile.put("referralCode",  user.getReferralCode());
            profile.put("createdAt",     user.getCreatedAt() != null ? user.getCreatedAt().toString() : null);
            data.put("profile", profile);

            // Resumes (full content)
            data.put("resumes", resumeRepository.findByUserIdOrderByUpdatedAtDesc(user.getId())
                    .stream()
                    .map(r -> {
                        Map<String, Object> rm = new LinkedHashMap<>();
                        rm.put("id",          r.getId());
                        rm.put("title",       r.getTitle());
                        rm.put("template",    r.getTemplate());
                        rm.put("summary",     r.getSummary());
                        rm.put("personalInfo",  r.getPersonalInfo());
                        rm.put("experience",    r.getExperience());
                        rm.put("education",     r.getEducation());
                        rm.put("skills",        r.getSkills());
                        rm.put("projects",      r.getProjects());
                        rm.put("certifications", r.getCertifications());
                        rm.put("customSections", r.getCustomSections());
                        rm.put("createdAt",  r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
                        rm.put("updatedAt",  r.getUpdatedAt() != null ? r.getUpdatedAt().toString() : null);
                        return rm;
                    })
                    .collect(Collectors.toList()));

            // Payment history (no sensitive card data — Razorpay never sends that to us)
            data.put("payments", paymentRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                    .stream()
                    .map(p -> {
                        Map<String, Object> pm = new LinkedHashMap<>();
                        pm.put("id",               p.getId());
                        pm.put("razorpayOrderId",  p.getRazorpayOrderId());
                        pm.put("razorpayPaymentId", p.getRazorpayPaymentId());
                        pm.put("amount",           p.getAmount());
                        pm.put("currency",         p.getCurrency());
                        pm.put("status",           p.getStatus());
                        pm.put("createdAt",        p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
                        return pm;
                    })
                    .collect(Collectors.toList()));

            // Export history
            data.put("exportHistory", exportHistoryRepository
                    .findByUserIdOrderByCreatedAtDesc(user.getId())
                    .stream()
                    .map(e -> Map.of(
                            "id",           e.getId(),
                            "resumeId",     e.getResumeId(),
                            "exportFormat", e.getExportFormat(),
                            "createdAt",    e.getCreatedAt() != null ? e.getCreatedAt().toString() : ""
                    ))
                    .collect(Collectors.toList()));

            byte[] json = objectMapper
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(data);

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"resumeforgeai_data_" + user.getId() + ".json\"")
                    .body(json);

        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("{\"success\":false,\"message\":\"Export failed\"}"
                            .getBytes(StandardCharsets.UTF_8));
        }
    }

    // -------------------------------------------------------------------------
    // DATA-02: Account deletion (cascade)
    // -------------------------------------------------------------------------

    @DeleteMapping
    @Transactional
    public ResponseEntity<ApiResponse> deleteAccount(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(401)
                    .body(ApiResponse.error("Authentication required"));
        }

        Long userId = user.getId();

        // 1. Delete snapshots for all user resumes (FK: resume_id → snapshots)
        resumeRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .forEach(r -> snapshotRepository.deleteByResumeId(r.getId()));

        // 2. Delete resumes
        resumeRepository.deleteByUserId(userId);

        // 3. Delete export history
        exportHistoryRepository.deleteByUserId(userId);

        // 4. Delete payments (keep for audit trail? — compliance choice;
        //    current decision: delete per GDPR right-to-erasure)
        paymentRepository.deleteByUserId(userId);

        // 5. Delete the user record itself
        userRepository.deleteById(userId);

        return ResponseEntity.ok(ApiResponse.success("Account and all associated data deleted successfully."));
    }
}