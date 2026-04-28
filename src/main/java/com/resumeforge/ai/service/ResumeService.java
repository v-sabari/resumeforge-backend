package com.resumeforge.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeforge.ai.dto.ResumeRequest;
import com.resumeforge.ai.dto.ResumeResponse;
import com.resumeforge.ai.dto.SnapshotResponse;
import com.resumeforge.ai.entity.Resume;
import com.resumeforge.ai.entity.ResumeSnapshot;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.ResourceNotFoundException;
import com.resumeforge.ai.repository.ResumeRepository;
import com.resumeforge.ai.repository.ResumeSnapshotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ResumeService {

    // B9 FIX: proper logger replaces System.err.println
    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ResumeSnapshotRepository snapshotRepository;

    @Autowired
    private ReferralService referralService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public ResumeResponse createResume(User user, ResumeRequest request) {
        Resume resume = Resume.builder()
                .userId(user.getId())
                .title(request.getTitle())
                .template(request.getTemplate() != null ? request.getTemplate() : "modern")
                .personalInfo(request.getPersonalInfo())
                .summary(request.getSummary())
                .experience(request.getExperience())
                .education(request.getEducation())
                .skills(request.getSkills())
                .projects(request.getProjects())
                .certifications(request.getCertifications())
                .customSections(request.getCustomSections())
                .build();

        resume = resumeRepository.save(resume);
        createSnapshot(resume);
        referralService.onFirstResumeCreated(user);
        return toResponse(resume);
    }

    public List<ResumeResponse> getAllResumes(User user) {
        return resumeRepository.findByUserIdOrderByUpdatedAtDesc(user.getId())
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
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

        resume.setTitle(request.getTitle());
        if (request.getTemplate() != null) {
            resume.setTemplate(request.getTemplate());
        }
        resume.setPersonalInfo(request.getPersonalInfo());
        resume.setSummary(request.getSummary());
        resume.setExperience(request.getExperience());
        resume.setEducation(request.getEducation());
        resume.setSkills(request.getSkills());
        resume.setProjects(request.getProjects());
        resume.setCertifications(request.getCertifications());
        resume.setCustomSections(request.getCustomSections());

        resume = resumeRepository.save(resume);
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
                .stream()
                .map(this::toSnapshotResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public ResumeResponse restoreSnapshot(User user, Long resumeId, Long snapshotId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));

        ResumeSnapshot snapshot = snapshotRepository.findByIdAndResumeId(snapshotId, resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("Snapshot not found"));

        try {
            ResumeRequest snapshotData = objectMapper.readValue(snapshot.getSnapshotData(), ResumeRequest.class);

            resume.setTitle(snapshotData.getTitle());
            resume.setTemplate(snapshotData.getTemplate());
            resume.setPersonalInfo(snapshotData.getPersonalInfo());
            resume.setSummary(snapshotData.getSummary());
            resume.setExperience(snapshotData.getExperience());
            resume.setEducation(snapshotData.getEducation());
            resume.setSkills(snapshotData.getSkills());
            resume.setProjects(snapshotData.getProjects());
            resume.setCertifications(snapshotData.getCertifications());
            resume.setCustomSections(snapshotData.getCustomSections());

            resume = resumeRepository.save(resume);
            createSnapshot(resume);
            return toResponse(resume);
        } catch (Exception e) {
            throw new RuntimeException("Failed to restore snapshot");
        }
    }

    private void createSnapshot(Resume resume) {
        try {
            ResumeRequest snapshotData = new ResumeRequest();
            snapshotData.setTitle(resume.getTitle());
            snapshotData.setTemplate(resume.getTemplate());
            snapshotData.setPersonalInfo(resume.getPersonalInfo());
            snapshotData.setSummary(resume.getSummary());
            snapshotData.setExperience(resume.getExperience());
            snapshotData.setEducation(resume.getEducation());
            snapshotData.setSkills(resume.getSkills());
            snapshotData.setProjects(resume.getProjects());
            snapshotData.setCertifications(resume.getCertifications());
            snapshotData.setCustomSections(resume.getCustomSections());

            String jsonData = objectMapper.writeValueAsString(snapshotData);

            ResumeSnapshot snapshot = ResumeSnapshot.builder()
                    .resumeId(resume.getId())
                    .snapshotData(jsonData)
                    .build();

            snapshotRepository.save(snapshot);

        } catch (Exception e) {
            // B9 FIX: was System.err.println — now properly logged via slf4j.
            // ERROR level because a missed snapshot means history will be silently
            // incomplete, which is a data-integrity issue worth alerting on.
            log.error("Failed to create snapshot for resumeId={}: {}", resume.getId(), e.getMessage(), e);
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
                .createdAt(resume.getCreatedAt())
                .updatedAt(resume.getUpdatedAt())
                .build();
    }

    private SnapshotResponse toSnapshotResponse(ResumeSnapshot snapshot) {
        return SnapshotResponse.builder()
                .id(snapshot.getId())
                .resumeId(snapshot.getResumeId())
                .snapshotData(snapshot.getSnapshotData())
                .createdAt(snapshot.getCreatedAt())
                .build();
    }
}