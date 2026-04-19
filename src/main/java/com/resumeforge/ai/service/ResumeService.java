package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.*;
import com.resumeforge.ai.exception.ResourceNotFoundException;
import com.resumeforge.ai.repository.ResumeRepository;
import com.resumeforge.ai.util.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ResumeService {
    private final ResumeRepository resumeRepository;
    private final JsonUtil jsonUtil;
    private final ReferralService referralService;

    public ResumeService(ResumeRepository resumeRepository,
                         JsonUtil jsonUtil,
                         ReferralService referralService) {
        this.resumeRepository = resumeRepository;
        this.jsonUtil         = jsonUtil;
        this.referralService  = referralService;
    }

    @Transactional(readOnly = true)
    public List<ResumeDto> getAll(User user) {
        return resumeRepository.findByUserOrderByUpdatedAtDesc(user)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ResumeDto getById(Long id, User user) {
        Resume resume = resumeRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));
        return toDto(resume);
    }

    @Transactional
    public ResumeDto create(User user, ResumeDto dto) {
        boolean isFirstResume = resumeRepository.countByUser(user) == 0;

        Resume resume = new Resume();
        resume.setUser(user);
        apply(resume, dto);
        ResumeDto saved = toDto(resumeRepository.save(resume));

        // ── Referral qualification hook ───────────────────────────────
        // Fires only on first resume creation. Silently swallowed if it
        // fails so it never breaks the resume creation response.
        if (isFirstResume) {
            try {
                referralService.checkAndQualifyReferral(user);
            } catch (Exception e) {
                // Log but don't fail the resume create operation
            }
        }

        return saved;
    }

    @Transactional
    public ResumeDto update(Long id, User user, ResumeDto dto) {
        Resume resume = resumeRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));
        apply(resume, dto);
        return toDto(resumeRepository.save(resume));
    }

    @Transactional
    public void delete(Long id, User user) {
        Resume resume = resumeRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));
        resumeRepository.delete(resume);
    }

    private void apply(Resume resume, ResumeDto dto) {
        resume.setFullName(dto.fullName());
        resume.setRole(dto.role());
        resume.setEmail(dto.email());
        resume.setPhone(dto.phone());
        resume.setLocation(dto.location());
        resume.setLinkedin(dto.linkedin());
        resume.setGithub(dto.github());
        resume.setPortfolio(dto.portfolio());
        resume.setSummary(dto.summary());
        resume.setSkillsJson(jsonUtil.toJson(dto.skills()));
        resume.setCertificationsJson(jsonUtil.toJson(dto.certifications()));
        resume.setAchievementsJson(jsonUtil.toJson(dto.achievements()));

        resume.getExperiences().clear();
        if (dto.experiences() != null) {
            for (ExperienceDto ed : dto.experiences()) {
                resume.getExperiences().add(Experience.builder()
                        .resume(resume).company(ed.company()).role(ed.role())
                        .location(ed.location()).startDate(ed.startDate()).endDate(ed.endDate())
                        .bulletsJson(jsonUtil.toJson(ed.bullets())).build());
            }
        }

        resume.getEducationEntries().clear();
        if (dto.education() != null) {
            for (EducationDto ed : dto.education()) {
                resume.getEducationEntries().add(Education.builder()
                        .resume(resume).institution(ed.institution()).degree(ed.degree())
                        .field(ed.field()).startDate(ed.startDate()).endDate(ed.endDate()).build());
            }
        }

        resume.getProjects().clear();
        if (dto.projects() != null) {
            for (ProjectDto pd : dto.projects()) {
                resume.getProjects().add(Project.builder()
                        .resume(resume).name(pd.name()).link(pd.link())
                        .description(pd.description()).build());
            }
        }
    }

    private ResumeDto toDto(Resume resume) {
        List<ExperienceDto> experiences = new ArrayList<>();
        for (Experience e : resume.getExperiences()) {
            experiences.add(new ExperienceDto(e.getId(), e.getCompany(), e.getRole(),
                    e.getLocation(), e.getStartDate(), e.getEndDate(),
                    jsonUtil.toStringList(e.getBulletsJson())));
        }
        List<EducationDto> education = new ArrayList<>();
        for (Education e : resume.getEducationEntries()) {
            education.add(new EducationDto(e.getId(), e.getInstitution(), e.getDegree(),
                    e.getField(), e.getStartDate(), e.getEndDate()));
        }
        List<ProjectDto> projects = new ArrayList<>();
        for (Project p : resume.getProjects()) {
            projects.add(new ProjectDto(p.getId(), p.getName(), p.getLink(), p.getDescription()));
        }
        return new ResumeDto(resume.getId(), resume.getFullName(), resume.getRole(),
                resume.getEmail(), resume.getPhone(), resume.getLocation(),
                resume.getLinkedin(), resume.getGithub(), resume.getPortfolio(),
                resume.getSummary(), jsonUtil.toStringList(resume.getSkillsJson()),
                experiences, education, projects,
                jsonUtil.toStringList(resume.getCertificationsJson()),
                jsonUtil.toStringList(resume.getAchievementsJson()),
                resume.getCreatedAt(), resume.getUpdatedAt());
    }
}
