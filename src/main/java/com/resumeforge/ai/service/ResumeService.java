package com.resumeforge.ai.service;

import com.resumeforge.ai.dto.EducationDto;
import com.resumeforge.ai.dto.ExperienceDto;
import com.resumeforge.ai.dto.ProjectDto;
import com.resumeforge.ai.dto.ResumeDto;
import com.resumeforge.ai.entity.Education;
import com.resumeforge.ai.entity.Experience;
import com.resumeforge.ai.entity.Project;
import com.resumeforge.ai.entity.Resume;
import com.resumeforge.ai.entity.User;
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

    public ResumeService(ResumeRepository resumeRepository, JsonUtil jsonUtil) {
        this.resumeRepository = resumeRepository;
        this.jsonUtil = jsonUtil;
    }

    @Transactional(readOnly = true)
    public List<ResumeDto> getAll(User user) {
        return resumeRepository.findByUserOrderByUpdatedAtDesc(user)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public ResumeDto getById(Long id, User user) {
        Resume resume = resumeRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));
        return toDto(resume);
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
            for (ExperienceDto experienceDto : dto.experiences()) {
                Experience exp = Experience.builder()
                        .resume(resume)
                        .company(experienceDto.company())
                        .role(experienceDto.role())
                        .location(experienceDto.location())
                        .startDate(experienceDto.startDate())
                        .endDate(experienceDto.endDate())
                        .bulletsJson(jsonUtil.toJson(experienceDto.bullets()))
                        .build();
                resume.getExperiences().add(exp);
            }
        }

        resume.getEducationEntries().clear();
        if (dto.education() != null) {
            for (EducationDto educationDto : dto.education()) {
                Education education = Education.builder()
                        .resume(resume)
                        .institution(educationDto.institution())
                        .degree(educationDto.degree())
                        .field(educationDto.field())
                        .startDate(educationDto.startDate())
                        .endDate(educationDto.endDate())
                        .build();
                resume.getEducationEntries().add(education);
            }
        }

        resume.getProjects().clear();
        if (dto.projects() != null) {
            for (ProjectDto projectDto : dto.projects()) {
                Project project = Project.builder()
                        .resume(resume)
                        .name(projectDto.name())
                        .link(projectDto.link())
                        .description(projectDto.description())
                        .build();
                resume.getProjects().add(project);
            }
        }
    }

    private ResumeDto toDto(Resume resume) {
        List<ExperienceDto> experiences = new ArrayList<>();
        for (Experience experience : resume.getExperiences()) {
            experiences.add(new ExperienceDto(
                    experience.getId(),
                    experience.getCompany(),
                    experience.getRole(),
                    experience.getLocation(),
                    experience.getStartDate(),
                    experience.getEndDate(),
                    jsonUtil.toStringList(experience.getBulletsJson())
            ));
        }

        List<EducationDto> education = new ArrayList<>();
        for (Education entry : resume.getEducationEntries()) {
            education.add(new EducationDto(
                    entry.getId(),
                    entry.getInstitution(),
                    entry.getDegree(),
                    entry.getField(),
                    entry.getStartDate(),
                    entry.getEndDate()
            ));
        }

        List<ProjectDto> projects = new ArrayList<>();
        for (Project project : resume.getProjects()) {
            projects.add(new ProjectDto(
                    project.getId(),
                    project.getName(),
                    project.getLink(),
                    project.getDescription()
            ));
        }

        return new ResumeDto(
                resume.getId(),
                resume.getFullName(),
                resume.getRole(),
                resume.getEmail(),
                resume.getPhone(),
                resume.getLocation(),
                resume.getLinkedin(),
                resume.getGithub(),
                resume.getPortfolio(),
                resume.getSummary(),
                jsonUtil.toStringList(resume.getSkillsJson()),
                experiences,
                education,
                projects,
                jsonUtil.toStringList(resume.getCertificationsJson()),
                jsonUtil.toStringList(resume.getAchievementsJson()),
                resume.getCreatedAt(),
                resume.getUpdatedAt()
        );
    }
}