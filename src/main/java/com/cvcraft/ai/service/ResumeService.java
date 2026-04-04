package com.cvcraft.ai.service;

import com.cvcraft.ai.dto.*;
import com.cvcraft.ai.entity.*;
import com.cvcraft.ai.exception.ResourceNotFoundException;
import com.cvcraft.ai.repository.ResumeRepository;
import com.cvcraft.ai.util.JsonUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final JsonUtil         jsonUtil;

    public ResumeService(ResumeRepository resumeRepository, JsonUtil jsonUtil) {
        this.resumeRepository = resumeRepository;
        this.jsonUtil         = jsonUtil;
    }

    @Transactional(readOnly = true)
    public List<ResumeDto> getAll(User user) {
        return resumeRepository.findByUserOrderByUpdatedAtDesc(user)
                .stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ResumeDto getById(Long id, User user) {
        return toDto(resumeRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found")));
    }

    @Transactional
    public ResumeDto create(User user, ResumeDto dto) {
        Resume resume = new Resume();
        resume.setUser(user);
        apply(resume, dto);
        return toDto(resumeRepository.save(resume));
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

    // ── Internal mapping ────────────────────────────────────────────

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

        // Experiences
        resume.getExperiences().clear();
        if (dto.experiences() != null) {
            for (ExperienceDto e : dto.experiences()) {
                resume.getExperiences().add(Experience.builder()
                        .resume(resume)
                        .company(e.company()).role(e.role()).location(e.location())
                        .startDate(e.startDate()).endDate(e.endDate())
                        .bulletsJson(jsonUtil.toJson(e.bullets()))
                        .build());
            }
        }

        // Education
        resume.getEducationEntries().clear();
        if (dto.education() != null) {
            for (EducationDto e : dto.education()) {
                resume.getEducationEntries().add(Education.builder()
                        .resume(resume)
                        .institution(e.institution()).degree(e.degree())
                        .field(e.field()).startDate(e.startDate()).endDate(e.endDate())
                        .build());
            }
        }

        // Projects
        resume.getProjects().clear();
        if (dto.projects() != null) {
            for (ProjectDto p : dto.projects()) {
                resume.getProjects().add(Project.builder()
                        .resume(resume)
                        .name(p.name()).link(p.link()).description(p.description())
                        .build());
            }
        }
    }

    private ResumeDto toDto(Resume r) {
        List<ExperienceDto> experiences = new ArrayList<>();
        for (Experience e : r.getExperiences()) {
            experiences.add(new ExperienceDto(e.getId(), e.getCompany(), e.getRole(),
                    e.getLocation(), e.getStartDate(), e.getEndDate(),
                    jsonUtil.toStringList(e.getBulletsJson())));
        }

        List<EducationDto> education = new ArrayList<>();
        for (Education e : r.getEducationEntries()) {
            education.add(new EducationDto(e.getId(), e.getInstitution(), e.getDegree(),
                    e.getField(), e.getStartDate(), e.getEndDate()));
        }

        List<ProjectDto> projects = new ArrayList<>();
        for (Project p : r.getProjects()) {
            projects.add(new ProjectDto(p.getId(), p.getName(), p.getLink(), p.getDescription()));
        }

        return new ResumeDto(
                r.getId(), r.getFullName(), r.getRole(), r.getEmail(), r.getPhone(),
                r.getLocation(), r.getLinkedin(), r.getGithub(), r.getPortfolio(), r.getSummary(),
                jsonUtil.toStringList(r.getSkillsJson()),
                experiences, education, projects,
                jsonUtil.toStringList(r.getCertificationsJson()),
                jsonUtil.toStringList(r.getAchievementsJson()),
                r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}
