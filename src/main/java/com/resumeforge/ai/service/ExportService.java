package com.resumeforge.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.ExportHistory;
import com.resumeforge.ai.entity.Resume;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.BadRequestException;
import com.resumeforge.ai.exception.ResourceNotFoundException;
import com.resumeforge.ai.repository.ExportHistoryRepository;
import com.resumeforge.ai.repository.ResumeRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ExportService {

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ExportHistoryRepository exportHistoryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ExportStatusResponse checkExportAccess(User user) {
        long recentExports = exportHistoryRepository.countRecentExports(
                user.getId(),
                LocalDateTime.now().minusDays(1)
        );

        boolean canExport = user.isPremium() || recentExports < 3;
        long limit = user.isPremium() ? 999 : 3;

        return ExportStatusResponse.builder()
                .canExport(canExport)
                .reason(canExport ? "OK" : "Daily limit reached. Upgrade to premium for unlimited exports.")
                .exportsToday(recentExports)
                .exportLimit(limit)
                .build();
    }

    @Transactional
    public ApiResponse recordExport(User user, ExportRecordRequest request) {
        Resume resume = resumeRepository.findByIdAndUserId(request.getResumeId(), user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));

        ExportHistory export = ExportHistory.builder()
                .userId(user.getId())
                .resumeId(resume.getId())
                .exportFormat(request.getFormat().toUpperCase())
                .build();

        exportHistoryRepository.save(export);

        return ApiResponse.success("Export recorded");
    }

    public List<ExportHistoryResponse> getExportHistory(User user) {
        return exportHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toHistoryResponse)
                .collect(Collectors.toList());
    }

    public byte[] exportToPdf(User user, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                float margin = 50;
                float yPosition = page.getMediaBox().getHeight() - margin;
                float fontSize = 12;

                // Title
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 18);
                contentStream.newLineAtOffset(margin, yPosition);
                contentStream.showText(resume.getTitle());
                contentStream.endText();
                yPosition -= 30;

                // Personal Info
                if (resume.getPersonalInfo() != null) {
                    JsonNode personalInfo = objectMapper.readTree(resume.getPersonalInfo());
                    yPosition = writeSection(contentStream, "Personal Information", personalInfo, margin, yPosition, fontSize);
                }

                // Summary
                if (resume.getSummary() != null && !resume.getSummary().isEmpty()) {
                    yPosition = writeTextSection(contentStream, "Summary", resume.getSummary(), margin, yPosition, fontSize);
                }

                // Experience
                if (resume.getExperience() != null) {
                    JsonNode experience = objectMapper.readTree(resume.getExperience());
                    yPosition = writeSection(contentStream, "Experience", experience, margin, yPosition, fontSize);
                }

                // Education
                if (resume.getEducation() != null) {
                    JsonNode education = objectMapper.readTree(resume.getEducation());
                    yPosition = writeSection(contentStream, "Education", education, margin, yPosition, fontSize);
                }

                // Skills
                if (resume.getSkills() != null) {
                    JsonNode skills = objectMapper.readTree(resume.getSkills());
                    yPosition = writeSection(contentStream, "Skills", skills, margin, yPosition, fontSize);
                }
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.save(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage());
        }
    }

    public byte[] exportToDocx(User user, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));

        try (XWPFDocument document = new XWPFDocument()) {
            // Title
            XWPFParagraph titleParagraph = document.createParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText(resume.getTitle());
            titleRun.setBold(true);
            titleRun.setFontSize(18);

            // Personal Info
            if (resume.getPersonalInfo() != null) {
                addJsonSection(document, "Personal Information", resume.getPersonalInfo());
            }

            // Summary
            if (resume.getSummary() != null && !resume.getSummary().isEmpty()) {
                addTextSection(document, "Summary", resume.getSummary());
            }

            // Experience
            if (resume.getExperience() != null) {
                addJsonSection(document, "Experience", resume.getExperience());
            }

            // Education
            if (resume.getEducation() != null) {
                addJsonSection(document, "Education", resume.getEducation());
            }

            // Skills
            if (resume.getSkills() != null) {
                addJsonSection(document, "Skills", resume.getSkills());
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            document.write(outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate DOCX: " + e.getMessage());
        }
    }

    public byte[] exportToTxt(User user, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));

        StringBuilder text = new StringBuilder();
        text.append(resume.getTitle()).append("\n\n");

        if (resume.getPersonalInfo() != null) {
            text.append("=== PERSONAL INFORMATION ===\n");
            text.append(flattenJson(resume.getPersonalInfo())).append("\n\n");
        }

        if (resume.getSummary() != null) {
            text.append("=== SUMMARY ===\n");
            text.append(resume.getSummary()).append("\n\n");
        }

        if (resume.getExperience() != null) {
            text.append("=== EXPERIENCE ===\n");
            text.append(flattenJson(resume.getExperience())).append("\n\n");
        }

        if (resume.getEducation() != null) {
            text.append("=== EDUCATION ===\n");
            text.append(flattenJson(resume.getEducation())).append("\n\n");
        }

        if (resume.getSkills() != null) {
            text.append("=== SKILLS ===\n");
            text.append(flattenJson(resume.getSkills())).append("\n\n");
        }

        return text.toString().getBytes();
    }

    private float writeSection(PDPageContentStream contentStream, String title, JsonNode data, float margin, float yPosition, float fontSize) throws Exception {
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), fontSize + 2);
        contentStream.newLineAtOffset(margin, yPosition);
        contentStream.showText(title);
        contentStream.endText();
        yPosition -= 20;

        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), fontSize);
        contentStream.newLineAtOffset(margin, yPosition);
        String content = data.toString().replace("\"", "").replace("{", "").replace("}", "");
        contentStream.showText(content.length() > 80 ? content.substring(0, 80) + "..." : content);
        contentStream.endText();
        yPosition -= 30;

        return yPosition;
    }

    private float writeTextSection(PDPageContentStream contentStream, String title, String text, float margin, float yPosition, float fontSize) throws Exception {
        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), fontSize + 2);
        contentStream.newLineAtOffset(margin, yPosition);
        contentStream.showText(title);
        contentStream.endText();
        yPosition -= 20;

        contentStream.beginText();
        contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), fontSize);
        contentStream.newLineAtOffset(margin, yPosition);
        String content = text.length() > 100 ? text.substring(0, 100) + "..." : text;
        contentStream.showText(content);
        contentStream.endText();
        yPosition -= 30;

        return yPosition;
    }

    private void addJsonSection(XWPFDocument document, String title, String jsonData) throws Exception {
        XWPFParagraph heading = document.createParagraph();
        XWPFRun headingRun = heading.createRun();
        headingRun.setText(title);
        headingRun.setBold(true);
        headingRun.setFontSize(14);

        XWPFParagraph content = document.createParagraph();
        XWPFRun contentRun = content.createRun();
        contentRun.setText(flattenJson(jsonData));
    }

    private void addTextSection(XWPFDocument document, String title, String text) {
        XWPFParagraph heading = document.createParagraph();
        XWPFRun headingRun = heading.createRun();
        headingRun.setText(title);
        headingRun.setBold(true);
        headingRun.setFontSize(14);

        XWPFParagraph content = document.createParagraph();
        XWPFRun contentRun = content.createRun();
        contentRun.setText(text);
    }

    private String flattenJson(String jsonData) {
        try {
            JsonNode node = objectMapper.readTree(jsonData);
            return node.toString().replace("\"", "").replace("{", "").replace("}", "").replace(",", "\n");
        } catch (Exception e) {
            return jsonData;
        }
    }

    private ExportHistoryResponse toHistoryResponse(ExportHistory export) {
        return ExportHistoryResponse.builder()
                .id(export.getId())
                .resumeId(export.getResumeId())
                .exportFormat(export.getExportFormat())
                .createdAt(export.getCreatedAt())
                .build();
    }
}
