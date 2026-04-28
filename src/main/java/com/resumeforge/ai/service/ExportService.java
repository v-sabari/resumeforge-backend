package com.resumeforge.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.entity.ExportHistory;
import com.resumeforge.ai.entity.Resume;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.exception.ResourceNotFoundException;
import com.resumeforge.ai.repository.ExportHistoryRepository;
import com.resumeforge.ai.repository.ResumeRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.poi.xwpf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ExportService {

    // B7 FIX: layout constants — used by multi-page logic
    private static final float MARGIN          = 50f;
    private static final float PAGE_HEIGHT     = PDRectangle.A4.getHeight();
    private static final float PAGE_WIDTH      = PDRectangle.A4.getWidth();
    private static final float USABLE_WIDTH    = PAGE_WIDTH - (MARGIN * 2);
    private static final float FONT_SIZE       = 11f;
    private static final float HEADING_SIZE    = 13f;
    private static final float TITLE_SIZE      = 18f;
    private static final float LINE_HEIGHT     = FONT_SIZE * 1.5f;
    private static final float HEADING_GAP     = 8f;
    private static final float SECTION_GAP     = 16f;
    // Start a new page when yPosition drops below this threshold
    private static final float BOTTOM_MARGIN   = 60f;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private ExportHistoryRepository exportHistoryRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Public API — unchanged signatures
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // PDF export — B7 fix: full content, multi-page, word-wrap
    //              B8 fix: PDType0Font (Unicode) replaces PDType1Font (ASCII-only)
    // -------------------------------------------------------------------------

    public byte[] exportToPdf(User user, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));

        try (PDDocument document = new PDDocument()) {

            // Load Unicode-capable fonts from classpath resources.
            // Place DejaVuSans.ttf and DejaVuSans-Bold.ttf in:
            //   src/main/resources/fonts/
            PDType0Font fontRegular;
            PDType0Font fontBold;
            try (InputStream reg  = getClass().getResourceAsStream("/fonts/DejaVuSans.ttf");
                 InputStream bold = getClass().getResourceAsStream("/fonts/DejaVuSans-Bold.ttf")) {
                if (reg == null || bold == null) {
                    throw new IllegalStateException(
                            "Font files not found in classpath. " +
                                    "Place DejaVuSans.ttf and DejaVuSans-Bold.ttf under src/main/resources/fonts/");
                }
                fontRegular = PDType0Font.load(document, reg,  true);
                fontBold    = PDType0Font.load(document, bold, true);
            }

            // PdfWriter holds mutable paging state so helper methods can
            // transparently add new pages without threading concerns.
            PdfWriter writer = new PdfWriter(document, fontRegular, fontBold);

            // Title
            writer.writeLine(resume.getTitle(), fontBold, TITLE_SIZE);
            writer.moveDown(SECTION_GAP);

            // Personal Info
            if (resume.getPersonalInfo() != null) {
                JsonNode node = objectMapper.readTree(resume.getPersonalInfo());
                writer.writeHeading("Personal Information");
                writer.writeJsonNode(node);
                writer.moveDown(SECTION_GAP);
            }

            // Summary
            if (resume.getSummary() != null && !resume.getSummary().isEmpty()) {
                writer.writeHeading("Summary");
                writer.writeWrapped(resume.getSummary(), fontRegular, FONT_SIZE);
                writer.moveDown(SECTION_GAP);
            }

            // Experience
            if (resume.getExperience() != null) {
                JsonNode node = objectMapper.readTree(resume.getExperience());
                writer.writeHeading("Experience");
                writer.writeJsonNode(node);
                writer.moveDown(SECTION_GAP);
            }

            // Education
            if (resume.getEducation() != null) {
                JsonNode node = objectMapper.readTree(resume.getEducation());
                writer.writeHeading("Education");
                writer.writeJsonNode(node);
                writer.moveDown(SECTION_GAP);
            }

            // Skills
            if (resume.getSkills() != null) {
                JsonNode node = objectMapper.readTree(resume.getSkills());
                writer.writeHeading("Skills");
                writer.writeJsonNode(node);
            }

            writer.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // DOCX export — unchanged logic, kept exactly as original
    // -------------------------------------------------------------------------

    public byte[] exportToDocx(User user, Long resumeId) {
        Resume resume = resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));

        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph titleParagraph = document.createParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText(resume.getTitle());
            titleRun.setBold(true);
            titleRun.setFontSize(18);

            if (resume.getPersonalInfo() != null) {
                addJsonSection(document, "Personal Information", resume.getPersonalInfo());
            }
            if (resume.getSummary() != null && !resume.getSummary().isEmpty()) {
                addTextSection(document, "Summary", resume.getSummary());
            }
            if (resume.getExperience() != null) {
                addJsonSection(document, "Experience", resume.getExperience());
            }
            if (resume.getEducation() != null) {
                addJsonSection(document, "Education", resume.getEducation());
            }
            if (resume.getSkills() != null) {
                addJsonSection(document, "Skills", resume.getSkills());
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.write(out);
            return out.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate DOCX: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // TXT export — B12 fix: explicit UTF-8 charset (was platform-default)
    // -------------------------------------------------------------------------

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

        // B12 FIX: explicit UTF-8 — was getBytes() which uses platform default charset
        return text.toString().getBytes(StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // PdfWriter — inner class that owns multi-page state
    // -------------------------------------------------------------------------

    /**
     * Wraps PDDocument page/stream management so callers never worry about
     * yPosition or page boundaries. Automatically adds a new page when content
     * reaches BOTTOM_MARGIN.
     */
    private class PdfWriter {

        private final PDDocument   document;
        private final PDType0Font  fontRegular;
        private final PDType0Font  fontBold;
        private PDPage             currentPage;
        private PDPageContentStream stream;
        private float              y;

        PdfWriter(PDDocument document, PDType0Font fontRegular, PDType0Font fontBold) throws Exception {
            this.document    = document;
            this.fontRegular = fontRegular;
            this.fontBold    = fontBold;
            newPage();
        }

        private void newPage() throws Exception {
            if (stream != null) {
                stream.close();
            }
            currentPage = new PDPage(PDRectangle.A4);
            document.addPage(currentPage);
            stream = new PDPageContentStream(document, currentPage);
            y = PAGE_HEIGHT - MARGIN;
        }

        /** Ensure there is at least `needed` vertical space; start new page if not. */
        private void ensureSpace(float needed) throws Exception {
            if (y - needed < BOTTOM_MARGIN) {
                newPage();
            }
        }

        void moveDown(float amount) {
            y -= amount;
        }

        void writeHeading(String title) throws Exception {
            ensureSpace(HEADING_SIZE + HEADING_GAP + LINE_HEIGHT);
            stream.beginText();
            stream.setFont(fontBold, HEADING_SIZE);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(title);
            stream.endText();
            y -= (HEADING_SIZE + HEADING_GAP);
        }

        void writeLine(String text, PDType0Font font, float size) throws Exception {
            ensureSpace(size + LINE_HEIGHT);
            stream.beginText();
            stream.setFont(font, size);
            stream.newLineAtOffset(MARGIN, y);
            stream.showText(sanitize(text));
            stream.endText();
            y -= (size + LINE_HEIGHT);
        }

        /**
         * B7 FIX: word-wrap long lines so no content is ever truncated.
         * Breaks text on whitespace to fit within USABLE_WIDTH at the given font/size.
         */
        void writeWrapped(String text, PDType0Font font, float size) throws Exception {
            if (text == null || text.isBlank()) return;

            List<String> lines = wordWrap(sanitize(text), font, size, USABLE_WIDTH);
            for (String line : lines) {
                ensureSpace(size + LINE_HEIGHT);
                stream.beginText();
                stream.setFont(font, size);
                stream.newLineAtOffset(MARGIN, y);
                stream.showText(line);
                stream.endText();
                y -= (size + LINE_HEIGHT);
            }
        }

        /**
         * Renders a JsonNode: objects print key: value pairs; arrays iterate
         * elements; primitives print their text value. All go through writeWrapped
         * so nothing is ever truncated.
         */
        void writeJsonNode(JsonNode node) throws Exception {
            if (node == null) return;

            if (node.isObject()) {
                var fields = node.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    String key   = capitalise(entry.getKey());
                    JsonNode val = entry.getValue();
                    if (val.isValueNode()) {
                        writeWrapped(key + ": " + val.asText(), fontRegular, FONT_SIZE);
                    } else if (val.isArray()) {
                        writeWrapped(key + ":", fontRegular, FONT_SIZE);
                        for (JsonNode item : val) {
                            writeWrapped("  • " + (item.isValueNode() ? item.asText() : flattenNode(item)),
                                    fontRegular, FONT_SIZE);
                        }
                    } else {
                        writeWrapped(key + ":", fontRegular, FONT_SIZE);
                        writeJsonNode(val);
                    }
                }
            } else if (node.isArray()) {
                for (JsonNode item : node) {
                    writeJsonNode(item);
                    y -= HEADING_GAP; // small gap between array items
                }
            } else {
                writeWrapped(node.asText(), fontRegular, FONT_SIZE);
            }
        }

        void close() throws Exception {
            if (stream != null) {
                stream.close();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Word-wrap helper
    // -------------------------------------------------------------------------

    /**
     * Splits text into lines that each fit within maxWidth at the given font/size.
     * Breaks on whitespace; words longer than maxWidth are placed on their own line.
     */
    private List<String> wordWrap(String text, PDType0Font font, float fontSize, float maxWidth) throws Exception {
        List<String> lines = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            String candidate = currentLine.isEmpty() ? word : currentLine + " " + word;
            float width = font.getStringWidth(candidate) / 1000 * fontSize;

            if (width <= maxWidth) {
                currentLine = new StringBuilder(candidate);
            } else {
                if (!currentLine.isEmpty()) {
                    lines.add(currentLine.toString());
                }
                // If a single word is wider than maxWidth, add it anyway (no infinite loop)
                currentLine = new StringBuilder(word);
            }
        }

        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }

        return lines;
    }

    // -------------------------------------------------------------------------
    // DOCX helpers — unchanged from original
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    private String flattenJson(String jsonData) {
        try {
            JsonNode node = objectMapper.readTree(jsonData);
            return node.toString()
                    .replace("\"", "")
                    .replace("{", "")
                    .replace("}", "")
                    .replace(",", "\n");
        } catch (Exception e) {
            return jsonData;
        }
    }

    private String flattenNode(JsonNode node) {
        return node.toString()
                .replace("\"", "")
                .replace("{", "")
                .replace("}", "")
                .replace(",", ", ");
    }

    /**
     * Strips control characters that PDFBox cannot encode, preventing
     * IllegalArgumentException on characters like \r, \t, etc.
     */
    private String sanitize(String text) {
        if (text == null) return "";
        return text.replaceAll("[\\p{Cntrl}&&[^\n]]", " ").replace("\n", " ").trim();
    }

    private String capitalise(String key) {
        if (key == null || key.isEmpty()) return key;
        String spaced = key.replaceAll("([A-Z])", " $1").trim();
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
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