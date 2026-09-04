package com.resumeforge.ai.service;

import tools.jackson.databind.ObjectMapper;
import com.resumeforge.ai.dto.ResumeRequest;
import com.resumeforge.ai.dto.ResumeResponse;
import com.resumeforge.ai.dto.SnapshotResponse;
import com.resumeforge.ai.entity.Resume;
import com.resumeforge.ai.entity.ResumeSnapshot;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.BadRequestException;
import com.resumeforge.ai.exception.ResourceNotFoundException;
import com.resumeforge.ai.repository.ResumeRepository;
import com.resumeforge.ai.repository.ResumeSnapshotRepository;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    public static final Set<String> ALLOWED_TEMPLATES = Set.of(
            "modern", "corporate", "classic", "traditional", "minimal", "clean",
            "fresher", "graduate", "tech", "engineering",
            "executive", "leadership", "creative", "designer",
            "sleek", "contemporary", "academic", "research", "medical", "finance"
    );

    public static final Set<String> PREMIUM_TEMPLATES = Set.of(
            "executive", "leadership", "creative", "designer",
            "sleek", "contemporary", "academic", "research", "medical", "finance"
    );

    public static final String DEFAULT_TEMPLATE = "modern";

    private static final int TITLE_MAX_LENGTH   = 500;
    private static final int SUMMARY_MAX_LENGTH = 10000;

    @Autowired private ResumeRepository         resumeRepository;
    @Autowired private ResumeSnapshotRepository snapshotRepository;
    @Autowired private ReferralService          referralService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // =========================================================================
    // PUBLIC API
    // =========================================================================

    @Transactional
    public ResumeResponse createResume(User user, ResumeRequest request) {
        String safeTemplate = resolveTemplate(request.getTemplate(), null, user);
        log.info("createResume userId={} title='{}' template='{}'",
                user.getId(), request.getTitle(), safeTemplate);

        Resume resume = Resume.builder()
                .userId(user.getId())
                .title(sanitizeTitle(request.getTitle()))
                .template(safeTemplate)
                .personalInfo(sanitizeJsonb(request.getPersonalInfo()))
                .summary(sanitizeSummary(request.getSummary()))
                .experience(sanitizeJsonb(request.getExperience()))
                .education(sanitizeJsonb(request.getEducation()))
                .skills(sanitizeJsonb(request.getSkills()))
                .projects(sanitizeJsonb(request.getProjects()))
                .certifications(sanitizeJsonb(request.getCertifications()))
                .customSections(sanitizeJsonb(request.getCustomSections()))
                .achievements(sanitizeJsonb(request.getAchievements()))
                .languages(sanitizeJsonb(request.getLanguages()))
                // V20: persist section ordering/visibility/label config
                .sectionsConfig(sanitizeJsonb(request.getSectionsConfig()))
                // COMPRESS FEATURE: density scale (defaults to full size)
                .layoutScale(sanitizeLayoutScale(request.getLayoutScale()))
                .build();

        resume = resumeRepository.save(resume);
        createSnapshot(resume);
        referralService.onFirstResumeCreated(user);
        return toResponse(resume);
    }

    public List<ResumeResponse> getAllResumes(User user) {
        return resumeRepository.findByUserIdOrderByUpdatedAtDesc(user.getId())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public ResumeResponse getResume(User user, Long id) {
        Resume resume = resumeRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));
        return toResponse(resume);
    }

    @Transactional
    public ResumeResponse updateResume(User user, Long id, ResumeRequest request) {
        Resume resume = resumeRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));

        log.info("updateResume START resumeId={} userId={} incomingTemplate='{}' incomingTitle='{}'",
                id, user.getId(), request.getTemplate(),
                request.getTitle() != null
                        ? request.getTitle().substring(0, Math.min(50, request.getTitle().length()))
                        : "null");

        String safeTemplate = resolveTemplate(request.getTemplate(), resume.getTemplate(), user);
        String safeTitle    = sanitizeTitle(request.getTitle());

        resume.setTitle(safeTitle);
        resume.setTemplate(safeTemplate);
        resume.setPersonalInfo(sanitizeJsonb(request.getPersonalInfo()));
        resume.setSummary(sanitizeSummary(request.getSummary()));
        resume.setExperience(sanitizeJsonb(request.getExperience()));
        resume.setEducation(sanitizeJsonb(request.getEducation()));
        resume.setSkills(sanitizeJsonb(request.getSkills()));
        resume.setProjects(sanitizeJsonb(request.getProjects()));
        resume.setCertifications(sanitizeJsonb(request.getCertifications()));
        resume.setCustomSections(sanitizeJsonb(request.getCustomSections()));
        resume.setAchievements(sanitizeJsonb(request.getAchievements()));
        resume.setLanguages(sanitizeJsonb(request.getLanguages()));
        // V20: update section config on every save
        resume.setSectionsConfig(sanitizeJsonb(request.getSectionsConfig()));
        // COMPRESS FEATURE: update density scale on every save
        resume.setLayoutScale(sanitizeLayoutScale(request.getLayoutScale()));

        try {
            resume = resumeRepository.save(resume);
            log.info("updateResume SUCCESS resumeId={} updatedAt={}", id, resume.getUpdatedAt());
        } catch (DataIntegrityViolationException ex) {
            // SEC FIX: root cause (constraint/column names) is logged server-side
            // but never echoed to the client — it leaks schema internals.
            log.error("updateResume DB_CONSTRAINT_VIOLATION resumeId={}", id, ex);
            throw new BadRequestException(
                    "Resume update failed due to a data constraint violation. " +
                    "Check required fields and value lengths.");
        } catch (ConstraintViolationException ex) {
            log.error("updateResume CONSTRAINT_VIOLATION resumeId={}", id, ex);
            throw new BadRequestException(
                    "Resume update failed: " +
                            ex.getConstraintViolations().stream()
                                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                                    .collect(Collectors.joining("; ")));
        } catch (Exception ex) {
            log.error("updateResume UNEXPECTED_ERROR resumeId={}", id, ex);
            throw ex;
        }

        createSnapshot(resume);
        return toResponse(resume);
    }

    @Transactional
    public void deleteResume(User user, Long id) {
        Resume resume = resumeRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));
        resumeRepository.delete(resume);
    }

    public List<SnapshotResponse> getResumeHistory(User user, Long resumeId) {
        resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));
        return snapshotRepository.findByResumeIdOrderByCreatedAtDesc(resumeId)
                .stream().map(this::toSnapshotResponse).collect(Collectors.toList());
    }

    @Transactional
    public ResumeResponse restoreSnapshot(User user, Long resumeId, Long snapshotId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));
        ResumeSnapshot snapshot = snapshotRepository.findByIdAndResumeId(snapshotId, resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("Snapshot not found"));
        try {
            ResumeRequest sd = objectMapper.readValue(snapshot.getSnapshotData(), ResumeRequest.class);
            resume.setTitle(sanitizeTitle(sd.getTitle()));
            resume.setTemplate(resolveTemplate(sd.getTemplate(), resume.getTemplate(), user));
            resume.setPersonalInfo(sanitizeJsonb(sd.getPersonalInfo()));
            resume.setSummary(sanitizeSummary(sd.getSummary()));
            resume.setExperience(sanitizeJsonb(sd.getExperience()));
            resume.setEducation(sanitizeJsonb(sd.getEducation()));
            resume.setSkills(sanitizeJsonb(sd.getSkills()));
            resume.setProjects(sanitizeJsonb(sd.getProjects()));
            resume.setCertifications(sanitizeJsonb(sd.getCertifications()));
            resume.setCustomSections(sanitizeJsonb(sd.getCustomSections()));
            resume.setAchievements(sanitizeJsonb(sd.getAchievements()));
            resume.setLanguages(sanitizeJsonb(sd.getLanguages()));
            resume.setSectionsConfig(sanitizeJsonb(sd.getSectionsConfig()));
            resume.setLayoutScale(sanitizeLayoutScale(sd.getLayoutScale()));
            resume = resumeRepository.save(resume);
            createSnapshot(resume);
            return toResponse(resume);
        } catch (Exception e) {
            log.error("restoreSnapshot FAILED resumeId={} snapshotId={}", resumeId, snapshotId, e);
            throw new RuntimeException("Failed to restore snapshot: " + e.getMessage());
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private String resolveTemplate(String incoming, String existing, User user) {
        if (incoming != null && !incoming.isBlank()) {
            String trimmed = incoming.trim().toLowerCase();
            if (ALLOWED_TEMPLATES.contains(trimmed)) {
                if (PREMIUM_TEMPLATES.contains(trimmed) && !user.isPremium()) {
                    log.warn("resolveTemplate PREMIUM_BLOCKED incoming='{}' userId={} — falling back",
                            incoming, user.getId());
                    // Free user trying a premium template — keep existing or default
                    if (existing != null && !existing.isBlank() && ALLOWED_TEMPLATES.contains(existing.trim())) {
                        return existing.trim();
                    }
                    return DEFAULT_TEMPLATE;
                }
                return trimmed;
            }
            log.warn("resolveTemplate INVALID incoming='{}' — falling back", incoming);
        }
        if (existing != null && !existing.isBlank() && ALLOWED_TEMPLATES.contains(existing.trim())) {
            return existing.trim();
        }
        return DEFAULT_TEMPLATE;
    }

    private String sanitizeTitle(String title) {
        if (title == null || title.isBlank()) return "Untitled Resume";
        String trimmed = title.trim();
        return trimmed.length() > TITLE_MAX_LENGTH ? trimmed.substring(0, TITLE_MAX_LENGTH) : trimmed;
    }

    // COMPRESS FEATURE: clamp/validate independently of the DTO's bean
    // validation, so a directly-crafted request can't smuggle in an
    // unreadable (<0.5) or nonsensical (>1.0) density scale even if bean
    // validation were ever bypassed. Missing/invalid -> full size (1.0).
    // COMPRESS FEATURE: clamp/validate independently of the DTO's bean
    // validation, so a directly-crafted request can't smuggle in an
    // unreadable (<0.5, shrink direction) or unprofessionally oversized
    // (>1.35, grow direction) density scale even if bean validation were
    // ever bypassed. Missing/invalid -> full size / unchanged (1.0). Keep
    // these bounds in sync with frontend/src/utils/compression.js
    // MIN_SCALE (0.72, with slack to 0.5 here) / MAX_SCALE (1.35).
    private static final double LAYOUT_SCALE_FLOOR = 0.5;
    private static final double LAYOUT_SCALE_CEILING = 1.35;
    private Double sanitizeLayoutScale(Double value) {
        if (value == null || value.isNaN() || value < LAYOUT_SCALE_FLOOR || value > LAYOUT_SCALE_CEILING) return 1.0;
        return value;
    }

    private String sanitizeJsonb(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return (trimmed.isEmpty() || trimmed.equals("null")) ? null : trimmed;
    }

    private String sanitizeSummary(String summary) {
        if (summary == null) return null;
        String trimmed = summary.trim();
        if (trimmed.isEmpty()) return null;
        return trimmed.length() > SUMMARY_MAX_LENGTH ? trimmed.substring(0, SUMMARY_MAX_LENGTH) : trimmed;
    }

    private void createSnapshot(Resume resume) {
        try {
            ResumeRequest sd = new ResumeRequest();
            sd.setTitle(resume.getTitle());
            sd.setTemplate(resume.getTemplate());
            sd.setPersonalInfo(resume.getPersonalInfo());
            sd.setSummary(resume.getSummary());
            sd.setExperience(resume.getExperience());
            sd.setEducation(resume.getEducation());
            sd.setSkills(resume.getSkills());
            sd.setProjects(resume.getProjects());
            sd.setCertifications(resume.getCertifications());
            sd.setCustomSections(resume.getCustomSections());
            sd.setAchievements(resume.getAchievements());
            sd.setLanguages(resume.getLanguages());
            sd.setSectionsConfig(resume.getSectionsConfig());
            sd.setLayoutScale(resume.getLayoutScale());

            snapshotRepository.save(
                    ResumeSnapshot.builder()
                            .resumeId(resume.getId())
                            .snapshotData(objectMapper.writeValueAsString(sd))
                            .build());
        } catch (Exception e) {
            log.error("createSnapshot FAILED resumeId={}: {}", resume.getId(), e.getMessage(), e);
        }
    }

    private ResumeResponse toResponse(Resume resume) {
        return ResumeResponse.builder()
                .id(resume.getId())
                .userId(resume.getUserId())
                .title(resume.getTitle())
                .template(resume.getTemplate())
                .personalInfo(resume.getPersonalInfo())
                .summary(resume.getSummary())
                .experience(resume.getExperience())
                .education(resume.getEducation())
                .skills(resume.getSkills())
                .projects(resume.getProjects())
                .certifications(resume.getCertifications())
                .customSections(resume.getCustomSections())
                .achievements(resume.getAchievements())
                .languages(resume.getLanguages())
                .sectionsConfig(resume.getSectionsConfig())
                .layoutScale(resume.getLayoutScale())
                .createdAt(resume.getCreatedAt())
                .updatedAt(resume.getUpdatedAt())
                .build();
    }

    private SnapshotResponse toSnapshotResponse(ResumeSnapshot snapshot) {
        String label = snapshot.getCreatedAt() != null
                ? "Version – " + snapshot.getCreatedAt().toLocalDate()
                : "Version " + snapshot.getId();
        return SnapshotResponse.builder()
                .id(snapshot.getId())
                .snapshotId(snapshot.getId())
                .resumeId(snapshot.getResumeId())
                .label(label)
                .createdAt(snapshot.getCreatedAt())
                .build();
    }
}
