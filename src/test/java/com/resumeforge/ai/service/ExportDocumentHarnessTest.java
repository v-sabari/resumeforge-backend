package com.resumeforge.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.resumeforge.ai.entity.Resume;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.repository.ExportHistoryRepository;
import com.resumeforge.ai.repository.ResumeRepository;
import org.apache.poi.xwpf.usermodel.IRunElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Field-level export audit harness.
 *
 * <p>Runs the real {@link ExportService} DOCX/TXT renderers against the
 * maximum-coverage QA dataset (every input populated, 150+ character values,
 * multi-line content, Unicode/emoji, every custom content mode, every addable
 * standard section and renamed section labels). It writes the real exported
 * files into {@code qa-artifacts/export-audit/} and asserts that every field
 * value and every section heading survives, in sectionsConfig order, with no
 * placeholder or broken text.</p>
 */
@ExtendWith(MockitoExtension.class)
class ExportDocumentHarnessTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final long RESUME_ID = 1L;
    private static final long USER_ID = 7L;
    private static final List<String> PERSONAL_KEYS = List.of(
        "fullName", "professionalTitle", "email", "phone", "location",
        "linkedin", "github", "portfolio");

    @Mock private ResumeRepository resumeRepository;
    @Mock private ExportQuotaService exportQuotaService;
    @Mock private ExportHistoryRepository exportHistoryRepository;
    @InjectMocks private ExportService exportService;

    private JsonNode data;
    private User user;

    @BeforeEach
    void setUp() throws Exception {
        data = loadData();
        user = User.builder()
            .id(USER_ID)
            .name("QA Max Coverage")
            .email("qa@example.com")
            .passwordHash("not-used")
            .build();
        when(resumeRepository.findByIdAndUserId(RESUME_ID, USER_ID))
            .thenReturn(Optional.of(buildResume()));
    }

    @Test
    void docxRendersEveryFieldInOrder() throws Exception {
        byte[] bytes = exportService.exportToDocx(user, RESUME_ID);
        Path out = artifactsDir().resolve("resume-max-coverage.docx");
        Files.write(out, bytes);

        String text = docxText(bytes);
        Map<String, Boolean> results = audit(text);
        writeReport("DOCX", results, text);

        List<String> failures = failed(results);
        assertTrue(failures.isEmpty(), "DOCX export is missing fields:\n" + String.join("\n", failures));
    }

    @Test
    void txtRendersEveryFieldInOrder() throws Exception {
        byte[] bytes = exportService.exportToTxt(user, RESUME_ID);
        Path out = artifactsDir().resolve("resume-max-coverage.txt");
        Files.write(out, bytes);

        String text = new String(bytes, StandardCharsets.UTF_8);
        Map<String, Boolean> results = audit(text);
        writeReport("TXT", results, text);

        List<String> failures = failed(results);
        assertTrue(failures.isEmpty(), "TXT export is missing fields:\n" + String.join("\n", failures));
    }

    /* ── audit ─────────────────────────────────────────────────────── */

    private Map<String, Boolean> audit(String text) {
        Map<String, Boolean> results = new LinkedHashMap<>();

        for (String key : PERSONAL_KEYS) {
            check(results, "basics." + key, text, data.path(key).asText());
        }
        check(results, "summary", text, data.path("summary").asText());

        for (int i = 0; i < data.path("experience").size(); i++) {
            JsonNode e = data.path("experience").get(i);
            String p = "experience[" + i + "].";
            check(results, p + "role", text, e.path("role").asText());
            check(results, p + "company", text, e.path("company").asText());
            check(results, p + "location", text, e.path("location").asText());
            check(results, p + "employmentType", text, e.path("employmentType").asText());
            check(results, p + "summary", text, e.path("summary").asText());
            check(results, p + "duration", text, dateRange(e));
            for (int j = 0; j < e.path("bullets").size(); j++) {
                check(results, p + "bullets[" + j + "]", text, e.path("bullets").get(j).asText());
            }
        }

        for (int i = 0; i < data.path("projects").size(); i++) {
            JsonNode pj = data.path("projects").get(i);
            String p = "projects[" + i + "].";
            check(results, p + "name", text, pj.path("name").asText());
            check(results, p + "role", text, pj.path("role").asText());
            check(results, p + "techStack", text, pj.path("techStack").asText());
            check(results, p + "description", text, pj.path("description").asText());
            checkAny(results, p + "link", text, pj.path("link").asText());
            checkAny(results, p + "github", text, pj.path("github").asText());
            for (int j = 0; j < pj.path("highlights").size(); j++) {
                check(results, p + "highlights[" + j + "]", text, pj.path("highlights").get(j).asText());
            }
        }

        for (int i = 0; i < data.path("education").size(); i++) {
            JsonNode e = data.path("education").get(i);
            String p = "education[" + i + "].";
            check(results, p + "institution", text, e.path("institution").asText());
            check(results, p + "degree", text, e.path("degree").asText());
            check(results, p + "field", text, e.path("field").asText());
            check(results, p + "grade", text, e.path("grade").asText());
            check(results, p + "details", text, e.path("details").asText());
            check(results, p + "year", text, dateRange(e));
        }

        for (int i = 0; i < data.path("certifications").size(); i++) {
            JsonNode c = data.path("certifications").get(i);
            String p = "certifications[" + i + "].";
            check(results, p + "name", text, c.path("name").asText());
            check(results, p + "issuer", text, c.path("issuer").asText());
            check(results, p + "year", text, c.path("year").asText());
            checkAny(results, p + "credentialUrl", text, c.path("credentialUrl").asText());
        }

        for (int i = 0; i < data.path("skills").size(); i++) {
            check(results, "skills[" + i + "]", text, data.path("skills").get(i).asText());
        }
        for (int i = 0; i < data.path("achievements").size(); i++) {
            check(results, "achievements[" + i + "]", text, data.path("achievements").get(i).asText());
        }
        for (int i = 0; i < data.path("languages").size(); i++) {
            check(results, "languages[" + i + "]", text, data.path("languages").get(i).asText());
        }

        JsonNode custom = data.path("customSections");
        for (JsonNode section : data.path("sectionsConfig")) {
            if (!section.path("visible").asBoolean(true) || "basics".equals(section.path("key").asText())) continue;
            String id = section.path("id").asText(section.path("key").asText());
            String label = section.path("label").asText(id);
            results.put("heading:" + id, hasHeadingLine(text, label));
            auditCustom(results, id, custom.path(id), text);
        }

        results.put("sectionOrder", headingsInOrder(text));

        for (String broken : List.of("[object Object]", "undefined", "NaN", "Lorem ipsum", "Your Name")) {
            results.put("noBrokenText:" + broken, !text.contains(broken));
        }
        return results;
    }

    private void auditCustom(Map<String, Boolean> results, String id, JsonNode content, String text) {
        if (content == null || content.isMissingNode()) return;
        String mode = content.path("mode").asText("text");
        if ("grid".equals(mode)) {
            for (int i = 0; i < content.path("columns").size(); i++) {
                JsonNode col = content.path("columns").get(i);
                check(results, id + ".columns[" + i + "].label", text, col.path("label").asText());
                for (int j = 0; j < col.path("items").size(); j++) {
                    check(results, id + ".columns[" + i + "].items[" + j + "]",
                        text, col.path("items").get(j).asText());
                }
            }
        } else if ("entries".equals(mode)) {
            for (int i = 0; i < content.path("entries").size(); i++) {
                JsonNode en = content.path("entries").get(i);
                String p = id + ".entries[" + i + "].";
                check(results, p + "title", text, en.path("title").asText());
                check(results, p + "subtitle", text, en.path("subtitle").asText());
                check(results, p + "date", text, en.path("date").asText());
                check(results, p + "location", text, en.path("location").asText());
                for (int j = 0; j < en.path("bullets").size(); j++) {
                    check(results, p + "bullets[" + j + "]", text, en.path("bullets").get(j).asText());
                }
            }
        } else if ("bullets".equals(mode)) {
            for (int i = 0; i < content.path("items").size(); i++) {
                check(results, id + ".items[" + i + "]", text, content.path("items").get(i).asText());
            }
        } else {
            check(results, id + ".text", text, content.path("text").asText());
        }
    }

    /* ── helpers ───────────────────────────────────────────────────── */

    private void check(Map<String, Boolean> results, String field, String text, String raw) {
        String value = normalize(raw);
        if (value.isEmpty()) return;
        results.put(field, normalize(text).contains(value));
    }

    private void checkAny(Map<String, Boolean> results, String field, String text, String raw) {
        String value = normalize(raw);
        if (value.isEmpty()) return;
        String shortValue = normalize(shortUrl(raw));
        String flat = normalize(text);
        results.put(field, flat.contains(value) || flat.contains(shortValue));
    }

    private boolean hasHeadingLine(String text, String label) {
        String wanted = normalize(label).toUpperCase();
        for (String line : text.split("\\R")) {
            if (line.trim().equals(wanted)) return true;
        }
        return false;
    }

    private boolean headingsInOrder(String text) {
        String[] lines = text.split("\\R");
        int previous = -1;
        for (JsonNode section : data.path("sectionsConfig")) {
            if (!section.path("visible").asBoolean(true) || "basics".equals(section.path("key").asText())) continue;
            String wanted = normalize(section.path("label").asText(
                section.path("id").asText())).toUpperCase();
            int found = -1;
            for (int i = previous + 1; i < lines.length; i++) {
                if (lines[i].trim().equals(wanted)) { found = i; break; }
            }
            if (found < 0) return false;
            previous = found;
        }
        return true;
    }

    private List<String> failed(Map<String, Boolean> results) {
        List<String> failures = new ArrayList<>();
        results.forEach((field, ok) -> { if (!ok) failures.add(field); });
        return failures;
    }

    private void writeReport(String format, Map<String, Boolean> results, String text) throws Exception {
        long passed = results.values().stream().filter(Boolean::booleanValue).count();
        ObjectNode report = OM.createObjectNode();
        report.put("format", format);
        report.put("generatedAt", java.time.Instant.now().toString());
        report.put("source", "src/test/resources/qa/resume-max-coverage.json");
        report.put("checks", results.size());
        report.put("passed", passed);
        report.put("failed", results.size() - passed);
        ObjectNode checks = report.putObject("results");
        results.forEach(checks::put);
        report.put("textChars", text.length());
        Files.writeString(
            artifactsDir().resolve("report-" + format.toLowerCase() + ".json"),
            OM.writerWithDefaultPrettyPrinter().writeValueAsString(report),
            StandardCharsets.UTF_8);
    }

    private Path artifactsDir() throws Exception {
        Path dir = Path.of(System.getProperty("user.dir"), "qa-artifacts", "export-audit");
        Files.createDirectories(dir);
        return dir;
    }

    private JsonNode loadData() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/qa/resume-max-coverage.json")) {
            if (in == null) throw new IllegalStateException("Missing /qa/resume-max-coverage.json");
            return OM.readTree(in);
        }
    }

    private Resume buildResume() throws Exception {
        ObjectNode personal = OM.createObjectNode();
        for (String key : PERSONAL_KEYS) personal.set(key, data.path(key));
        return Resume.builder()
            .id(RESUME_ID)
            .userId(USER_ID)
            .title("QA Max Coverage Resume")
            .template(data.path("template").asText("modern"))
            .personalInfo(OM.writeValueAsString(personal))
            .summary(data.path("summary").asText(""))
            .experience(data.path("experience").toString())
            .education(data.path("education").toString())
            .skills(data.path("skills").toString())
            .projects(data.path("projects").toString())
            .certifications(data.path("certifications").toString())
            .customSections(data.path("customSections").toString())
            .achievements(data.path("achievements").toString())
            .languages(data.path("languages").toString())
            .sectionsConfig(data.path("sectionsConfig").toString())
            .fontFamily(data.path("fontFamily").asText("template"))
            .fontSize(data.path("fontSize").asDouble(10.5))
            .build();
    }

    private String docxText(byte[] bytes) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            for (XWPFParagraph paragraph : doc.getParagraphs()) {
                for (IRunElement element : paragraph.getIRuns()) {
                    if (element instanceof XWPFRun run) {
                        sb.append(run.text()).append(' ');
                    }
                }
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String dateRange(JsonNode node) {
        String start = node.path("startDate").asText("").trim();
        String end = node.path("endDate").asText("").trim();
        if (start.isEmpty() && end.isEmpty()) return "";
        if (start.isEmpty()) return end;
        if (end.isEmpty()) return start;
        return start + " \u2013 " + end;
    }

    private String shortUrl(String url) {
        return url == null ? "" : url.trim()
            .replaceFirst("(?i)^https?://", "")
            .replaceFirst("(?i)^www\\.", "")
            .replaceFirst("/+$", "");
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }
}
