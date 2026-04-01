package com.resumeforge.ai.service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
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

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class PdfExportService {
    private final ResumeRepository resumeRepository;
    private final JsonUtil jsonUtil;

    public PdfExportService(ResumeRepository resumeRepository, JsonUtil jsonUtil) {
        this.resumeRepository = resumeRepository;
        this.jsonUtil = jsonUtil;
    }

    @Transactional(readOnly = true)
    public byte[] generateResumePdf(Long resumeId, User user) {
        Resume resume = resumeRepository.findByIdAndUser(resumeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, outputStream);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 11);

            addLine(document, safe(resume.getFullName()), titleFont, 12);
            addLine(document, safe(resume.getRole()), bodyFont, 6);

            String contactLine = joinNonBlank(" • ",
                    resume.getEmail(),
                    resume.getPhone(),
                    resume.getLocation(),
                    resume.getLinkedin(),
                    resume.getGithub(),
                    resume.getPortfolio()
            );
            addLine(document, contactLine, bodyFont, 12);

            addSection(document, "Professional Summary", sectionFont);
            addLine(document, safe(resume.getSummary()), bodyFont, 10);

            List<String> skills = jsonUtil.toStringList(resume.getSkillsJson());
            if (!skills.isEmpty()) {
                addSection(document, "Skills", sectionFont);
                addLine(document, String.join(", ", skills), bodyFont, 10);
            }

            if (!resume.getExperiences().isEmpty()) {
                addSection(document, "Experience", sectionFont);
                for (Experience experience : resume.getExperiences()) {
                    String heading = joinNonBlank(" — ",
                            experience.getRole(),
                            experience.getCompany()
                    );
                    addLine(document, heading, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11), 2);

                    String meta = joinNonBlank(" | ",
                            experience.getLocation(),
                            joinNonBlank(" - ", experience.getStartDate(), experience.getEndDate())
                    );
                    addLine(document, meta, bodyFont, 4);

                    List<String> bullets = jsonUtil.toStringList(experience.getBulletsJson());
                    for (String bullet : bullets) {
                        addLine(document, "• " + safe(bullet), bodyFont, 2);
                    }

                    addSpacer(document, 6);
                }
            }

            if (!resume.getProjects().isEmpty()) {
                addSection(document, "Projects", sectionFont);
                for (Project project : resume.getProjects()) {
                    addLine(document, safe(project.getName()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11), 2);
                    addLine(document, safe(project.getLink()), bodyFont, 2);
                    addLine(document, safe(project.getDescription()), bodyFont, 6);
                }
            }

            if (!resume.getEducationEntries().isEmpty()) {
                addSection(document, "Education", sectionFont);
                for (Education education : resume.getEducationEntries()) {
                    String heading = joinNonBlank(" — ",
                            education.getDegree(),
                            education.getInstitution()
                    );
                    addLine(document, heading, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11), 2);

                    String meta = joinNonBlank(" | ",
                            safe(education.getField()),
                            joinNonBlank(" - ", education.getStartDate(), education.getEndDate())
                    );
                    addLine(document, meta, bodyFont, 6);
                }
            }

            List<String> certifications = jsonUtil.toStringList(resume.getCertificationsJson());
            if (!certifications.isEmpty()) {
                addSection(document, "Certifications", sectionFont);
                for (String certification : certifications) {
                    addLine(document, "• " + safe(certification), bodyFont, 2);
                }
                addSpacer(document, 6);
            }

            List<String> achievements = jsonUtil.toStringList(resume.getAchievementsJson());
            if (!achievements.isEmpty()) {
                addSection(document, "Achievements", sectionFont);
                for (String achievement : achievements) {
                    addLine(document, "• " + safe(achievement), bodyFont, 2);
                }
            }

            document.close();
            return outputStream.toByteArray();
        } catch (DocumentException exception) {
            throw new RuntimeException("Failed to generate PDF", exception);
        } catch (Exception exception) {
            throw new RuntimeException("Failed to export resume PDF", exception);
        }
    }

    public String buildFilename(Long resumeId, User user) {
        Resume resume = resumeRepository.findByIdAndUser(resumeId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));

        String base = safe(resume.getFullName()).trim();
        if (base.isBlank()) {
            base = "resume";
        }

        String sanitized = base
                .replaceAll("[^a-zA-Z0-9\\-\\s_]", "")
                .trim()
                .replaceAll("\\s+", "_");

        if (sanitized.isBlank()) {
            sanitized = "resume";
        }

        return sanitized + ".pdf";
    }

    private void addSection(Document document, String title, Font font) throws DocumentException {
        addSpacer(document, 4);
        addLine(document, title, font, 6);
    }

    private void addLine(Document document, String text, Font font, float spacingAfter) throws DocumentException {
        if (text == null || text.isBlank()) {
            return;
        }
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setSpacingAfter(spacingAfter);
        document.add(paragraph);
    }

    private void addSpacer(Document document, float spacingAfter) throws DocumentException {
        Paragraph spacer = new Paragraph(" ", FontFactory.getFont(FontFactory.HELVETICA, 2));
        spacer.setSpacingAfter(spacingAfter);
        document.add(spacer);
    }

    private String safe(String value) {
        return value == null ? "" : new String(value.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    private String joinNonBlank(String delimiter, String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                if (!builder.isEmpty()) {
                    builder.append(delimiter);
                }
                builder.append(value.trim());
            }
        }
        return builder.toString();
    }
}