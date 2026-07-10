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
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPageMar;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * ExportService — PDF, DOCX, TXT export.
 *
 * Fully renders structured resume data (personal info, standard sections,
 * AND user-defined custom sections) respecting sectionsConfig order/visibility.
 * Custom sections (type=custom) are rendered after standard sections using
 * their label and stored content (mode=text → paragraph; mode=bullets → list).
 */
@Service
public class ExportService {

    /* ── PDF layout constants ─────────────────────────────────────── */
    private static final float MARGIN       = 50f;
    private static final float PAGE_H       = PDRectangle.A4.getHeight();
    private static final float PAGE_W       = PDRectangle.A4.getWidth();
    private static final float USABLE_W     = PAGE_W - MARGIN * 2;
    private static final float FONT_SIZE    = 10.5f;
    private static final float SMALL_SIZE   = 9f;
    private static final float HEADING_SIZE = 12.5f;
    private static final float NAME_SIZE    = 21f;
    private static final float TITLE_SIZE   = 12f;
    private static final float LINE_H       = FONT_SIZE * 1.4f;
    private static final float HEADING_GAP  = 7f;
    private static final float SECTION_GAP  = 14f;
    private static final float ITEM_GAP     = 6f;
    private static final float BOTTOM_MARGIN= 60f;

    @Autowired private ResumeRepository        resumeRepository;
    @Autowired private ExportHistoryRepository exportHistoryRepository;

    private final ObjectMapper om = new ObjectMapper();

    /* ── Render service (headless-Chromium PDF renderer) config ─────
       ISSUE FIX (export/preview parity): PDF export previously used a
       hand-rolled PDFBox layout that never referenced `template`, so every
       export looked identical regardless of which of the 6 preview
       templates the user picked. exportToPdf() now delegates to
       render-service, a small internal Node/Playwright service that
       server-renders the SAME React template components used by the
       browser preview and prints them to PDF — guaranteeing visual parity
       instead of approximating it. See render-service/README.md.
       If the render service is unreachable, we fail loudly rather than
       silently falling back to the legacy mismatched layout — a resume
       download that doesn't match what the user reviewed is worse than an
       error the user can retry. */
    @Value("${resume.render-service.url:http://localhost:4100}")
    private String renderServiceUrl;

    @Value("${resume.render-service.timeout-seconds:15}")
    private int renderServiceTimeoutSeconds;

    // Shared secret sent as a header so only this backend can invoke the
    // render endpoint — needed because it now lives at a public Vercel URL
    // (api/render-pdf.js) rather than a Render private service with no
    // public URL at all. Must match RENDER_INTERNAL_SECRET set on Vercel.
    @Value("${resume.render-service.internal-secret:}")
    private String renderServiceInternalSecret;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /* ================================================================
       PUBLIC API — access / history (unchanged)
       ================================================================ */

    public ExportStatusResponse checkExportAccess(User user) {
        long used  = countRecent(user);
        boolean ok = user.isPremium() || used < 3;
        long limit = user.isPremium() ? 999 : 3;
        return ExportStatusResponse.builder()
                .canExport(ok)
                .reason(ok ? "OK" : "Daily limit reached. Upgrade to premium for unlimited exports.")
                .exportsToday(used).exportLimit(limit)
                .remainingFreeExports(user.isPremium() ? 999 : Math.max(0, limit - used))
                .build();
    }

    @Transactional
    public ExportStatusResponse checkAndRecordExport(User user, Long resumeId, String format) {
        long used  = countRecent(user);
        boolean ok = user.isPremium() || used < 3;
        long limit = user.isPremium() ? 999 : 3;
        if (!ok) return ExportStatusResponse.builder().canExport(false)
                .reason("Daily limit reached. Upgrade to premium for unlimited exports.")
                .exportsToday(used).exportLimit(limit).remainingFreeExports(0).build();
        Resume r = load(user, resumeId);
        exportHistoryRepository.save(ExportHistory.builder()
                .userId(user.getId()).resumeId(r.getId()).exportFormat(format.toUpperCase()).build());
        return ExportStatusResponse.builder().canExport(true).reason("OK")
                .exportsToday(used + 1).exportLimit(limit)
                .remainingFreeExports(user.isPremium() ? 999 : Math.max(0, limit - used - 1))
                .build();
    }

    @Transactional
    public ApiResponse recordExport(User user, ExportRecordRequest request) {
        Resume r = load(user, request.getResumeId());
        exportHistoryRepository.save(ExportHistory.builder()
                .userId(user.getId()).resumeId(r.getId())
                .exportFormat(request.getFormat().toUpperCase()).build());
        return ApiResponse.success("Export recorded");
    }

    public List<ExportHistoryResponse> getExportHistory(User user) {
        return exportHistoryRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toHistoryResponse).collect(Collectors.toList());
    }

    public String safeFilename(User user, Long resumeId) {
        return resumeRepository.findByIdAndUserId(resumeId, user.getId())
                .map(r -> sanitizeFilename(r.getTitle())).orElse("resume");
    }

    /* ================================================================
       PDF EXPORT
       ================================================================ */

    public byte[] exportToPdf(User user, Long resumeId) {
        Resume resume = load(user, resumeId);
        Map<String, Object> payload = buildRenderPayload(resume);

        try {
            String body = om.writeValueAsString(payload);
            // NOTE: renderServiceUrl now points at the FULL endpoint path
            // (e.g. https://your-frontend.vercel.app/api/render-pdf), not a
            // base URL — the old "/render/pdf" suffix has been removed since
            // the new Vercel function route is already the complete path.
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(renderServiceUrl))
                    .timeout(Duration.ofSeconds(renderServiceTimeoutSeconds))
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Secret", renderServiceInternalSecret)
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() != 200) {
                throw new RuntimeException("Render service returned HTTP " + resp.statusCode()
                        + ": " + new String(resp.body(), StandardCharsets.UTF_8));
            }
            return resp.body();
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            // Deliberately NOT falling back to exportToPdfLegacy here: a PDF
            // that doesn't match the template the user just previewed is a
            // worse outcome than a failed download the user can retry. See
            // render-service/README.md for operational runbook.
            throw new RuntimeException("Failed to generate PDF via render service: " + e.getMessage(), e);
        }
    }

    /**
     * Flattens the entity's JSON columns into the single flat resume shape
     * that render-service (and the frontend editor/preview) expect:
     * personalInfo's fields hoisted to top level, arrays passed through
     * as-is (field names inside them — role/company/bullets/degree/field/
     * institution/grade/etc. — already match the frontend's raw field
     * names 1:1, confirmed against the legacy PDFBox reader below), plus
     * sectionsConfig, customSections and template.
     */
    private Map<String, Object> buildRenderPayload(Resume resume) {
        JsonNode pi = parseObj(resume.getPersonalInfo());
        Map<String, Object> flat = new LinkedHashMap<>();
        flat.put("fullName",          t(pi, "fullName"));
        flat.put("professionalTitle", t(pi, "professionalTitle").isBlank() ? t(pi, "title") : t(pi, "professionalTitle"));
        flat.put("email",             t(pi, "email"));
        flat.put("phone",             t(pi, "phone"));
        flat.put("location",          t(pi, "location"));
        flat.put("linkedin",          t(pi, "linkedin"));
        flat.put("github",            t(pi, "github"));
        flat.put("portfolio",         t(pi, "portfolio"));
        flat.put("summary",           resume.getSummary() == null ? "" : resume.getSummary());
        flat.put("skills",            parseArr(resume.getSkills()));
        flat.put("experience",        parseArr(resume.getExperience()));
        flat.put("education",         parseArr(resume.getEducation()));
        flat.put("projects",          parseArr(resume.getProjects()));
        flat.put("certifications",    parseArr(resume.getCertifications()));
        flat.put("achievements",      parseArr(resume.getAchievements()));
        flat.put("languages",         parseArr(resume.getLanguages()));
        flat.put("customSections",    parseObj(resume.getCustomSections()));
        flat.put("sectionsConfig",    parseArr(resume.getSectionsConfig()));
        // COMPRESS FEATURE: forward the exact, already-verified density
        // scale so the exported PDF paginates identically to what the user
        // confirmed in the live preview — never recomputed here, never
        // silently dropped. See Resume.java / renderPdfHandler.jsx.
        flat.put("layoutScale",       resume.getLayoutScale() == null ? 1.0 : resume.getLayoutScale());

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("resume", flat);
        req.put("template", resume.getTemplate() == null || resume.getTemplate().isBlank() ? "modern" : resume.getTemplate());
        return req;
    }

    /**
     * @deprecated Superseded by exportToPdf() calling render-service, which
     * renders the actual preview templates instead of this independent
     * hand-rolled layout. Kept only as a documented emergency fallback if
     * render-service is down for an extended period — NOT wired to any
     * controller. Re-enabling it means exports stop matching the preview
     * again, so treat that as a temporary, visible degradation, not a
     * silent one.
     */
    @Deprecated
    private byte[] exportToPdfLegacy(User user, Long resumeId) {
        Resume resume = load(user, resumeId);
        try (PDDocument doc = new PDDocument()) {
            PDType0Font reg, bold;
            try (InputStream ri = font("/fonts/DejaVuSans.ttf");
                 InputStream bi = font("/fonts/DejaVuSans-Bold.ttf")) {
                reg  = PDType0Font.load(doc, ri,  true);
                bold = PDType0Font.load(doc, bi, true);
            }
            PdfWriter w = new PdfWriter(doc, reg, bold);

            /* ── Header ─────────────────────────────────────────── */
            JsonNode pi = parseObj(resume.getPersonalInfo());
            w.writeLine(or(t(pi,"fullName"),"Your Name"), bold, NAME_SIZE);
            String ptitle = t(pi,"professionalTitle");
            if (!ptitle.isBlank()) w.writeLine(ptitle, reg, TITLE_SIZE);

            List<String> contact = parts(t(pi,"email"),t(pi,"phone"),t(pi,"location"));
            if (!contact.isEmpty()) w.writeWrapped(join(contact,"   |   "), reg, SMALL_SIZE);

            List<String> links = parts(t(pi,"linkedin"),t(pi,"github"),t(pi,"portfolio"));
            if (!links.isEmpty()) w.writeWrapped(join(links,"   |   "), reg, SMALL_SIZE);
            w.moveDown(SECTION_GAP);

            /* ── Ordered sections ───────────────────────────────── */
            List<JsonNode> sections = orderedVisible(resume.getSectionsConfig());
            JsonNode customSections = parseObj(resume.getCustomSections());

            for (JsonNode sec : sections) {
                String stype = t(sec,"type");
                String key   = t(sec,"key");
                String label = t(sec,"label");

                if ("custom".equals(stype)) {
                    renderPdfCustomSection(w, reg, bold, label, customSections.path(t(sec,"id")));
                    continue;
                }

                switch (key) {
                    case "basics": break; // already rendered in header

                    case "summary":
                        if (resume.getSummary()!=null && !resume.getSummary().isBlank()) {
                            w.writeHeading(label.isBlank()?"PROFESSIONAL SUMMARY":label.toUpperCase());
                            w.writeWrapped(resume.getSummary(), reg, FONT_SIZE);
                            w.moveDown(SECTION_GAP);
                        }
                        break;

                    case "experience":
                        renderPdfExperience(w, reg, bold, label, parseArr(resume.getExperience()));
                        break;

                    case "projects":
                        renderPdfProjects(w, reg, bold, label, parseArr(resume.getProjects()));
                        break;

                    case "education":
                        renderPdfEducation(w, reg, bold, label, parseArr(resume.getEducation()));
                        break;

                    case "skills":
                        renderPdfSkills(w, reg, label, parseArr(resume.getSkills()));
                        break;

                    case "achievements":
                        renderPdfList(w, reg, label, parseArr(resume.getAchievements()));
                        break;

                    case "languages":
                        renderPdfSkills(w, reg, label, parseArr(resume.getLanguages()));
                        break;

                    case "certifications":
                        renderPdfCertifications(w, reg, bold, label, parseArr(resume.getCertifications()));
                        break;

                    default:
                        // unknown standard key — render as custom if content exists
                        renderPdfCustomSection(w, reg, bold, label, customSections.path(key));
                        break;
                }
            }

            w.close();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate PDF: " + e.getMessage(), e);
        }
    }

    /* ── PDF section renderers ──────────────────────────────────── */

    private void renderPdfExperience(PdfWriter w, PDType0Font reg, PDType0Font bold,
                                     String label, JsonNode arr) throws Exception {
        if (!arr.isArray() || arr.size()==0) return;
        w.writeHeading(hl(label,"PROFESSIONAL EXPERIENCE"));
        for (JsonNode e : arr) {
            String role=t(e,"role"), company=t(e,"company"), dates=dateRange(e);
            String head = role.isBlank()?company : company.isBlank()?role : role+" — "+company;
            if (!head.isBlank()) w.writeTwoCol(head, dates, bold, FONT_SIZE);
            String meta = joinMeta(t(e,"location"),t(e,"employmentType"));
            if (!meta.isBlank()) w.writeWrapped(meta, reg, SMALL_SIZE);
            if (!t(e,"summary").isBlank()) w.writeWrapped(t(e,"summary"), reg, FONT_SIZE);
            for (JsonNode b : e.path("bullets")) { String bl=b.asText(""); if(!bl.isBlank()) w.writeBullet(bl, reg, FONT_SIZE); }
            w.moveDown(ITEM_GAP);
        }
        w.moveDown(SECTION_GAP-ITEM_GAP);
    }

    private void renderPdfProjects(PdfWriter w, PDType0Font reg, PDType0Font bold,
                                   String label, JsonNode arr) throws Exception {
        if (!arr.isArray() || arr.size()==0) return;
        w.writeHeading(hl(label,"PROJECTS"));
        for (JsonNode p : arr) {
            String name=t(p,"name"), role=t(p,"role");
            String head = role.isBlank()?name : name+"("+role+")";
            if (!head.isBlank()) w.writeLine(head, bold, FONT_SIZE);
            if (!t(p,"techStack").isBlank()) w.writeWrapped("Tech: "+t(p,"techStack"), reg, SMALL_SIZE);
            List<String> urls = parts(t(p,"link"),t(p,"github"));
            if (!urls.isEmpty()) w.writeWrapped(join(urls,"  ·  "), reg, SMALL_SIZE);
            if (!t(p,"description").isBlank()) w.writeWrapped(t(p,"description"), reg, FONT_SIZE);
            for (JsonNode h : p.path("highlights")) { String hl=h.asText(""); if(!hl.isBlank()) w.writeBullet(hl, reg, FONT_SIZE); }
            w.moveDown(ITEM_GAP);
        }
        w.moveDown(SECTION_GAP-ITEM_GAP);
    }

    private void renderPdfEducation(PdfWriter w, PDType0Font reg, PDType0Font bold,
                                    String label, JsonNode arr) throws Exception {
        if (!arr.isArray() || arr.size()==0) return;
        w.writeHeading(hl(label,"EDUCATION"));
        for (JsonNode e : arr) {
            String degree=t(e,"degree"), field=t(e,"field"), dates=dateRange(e);
            String head = field.isBlank()?degree : degree+" in "+field;
            if (!head.isBlank()) w.writeTwoCol(head, dates, bold, FONT_SIZE);
            if (!t(e,"institution").isBlank()) w.writeWrapped(t(e,"institution"), reg, FONT_SIZE);
            if (!t(e,"grade").isBlank()) w.writeWrapped("Grade: "+t(e,"grade"), reg, SMALL_SIZE);
            if (!t(e,"details").isBlank()) w.writeWrapped(t(e,"details"), reg, SMALL_SIZE);
            w.moveDown(ITEM_GAP);
        }
        w.moveDown(SECTION_GAP-ITEM_GAP);
    }

    private void renderPdfSkills(PdfWriter w, PDType0Font reg, String label, JsonNode arr) throws Exception {
        if (!arr.isArray() || arr.size()==0) return;
        w.writeHeading(hl(label,"SKILLS"));
        List<String> list = new ArrayList<>();
        for (JsonNode s : arr) { String v=s.asText(""); if(!v.isBlank()) list.add(v); }
        if (!list.isEmpty()) w.writeWrapped(join(list,"  ·  "), reg, FONT_SIZE);
        w.moveDown(SECTION_GAP);
    }

    private void renderPdfList(PdfWriter w, PDType0Font reg, String label, JsonNode arr) throws Exception {
        if (!arr.isArray() || arr.size()==0) return;
        w.writeHeading(hl(label,"ACHIEVEMENTS"));
        for (JsonNode a : arr) { String v=a.asText(""); if(!v.isBlank()) w.writeBullet(v, reg, FONT_SIZE); }
        w.moveDown(SECTION_GAP);
    }

    private void renderPdfCertifications(PdfWriter w, PDType0Font reg, PDType0Font bold,
                                         String label, JsonNode arr) throws Exception {
        if (!arr.isArray() || arr.size()==0) return;
        w.writeHeading(hl(label,"CERTIFICATIONS"));
        for (JsonNode c : arr) {
            String line = c.isTextual() ? c.asText("") : certLine(c);
            if (!line.isBlank()) w.writeWrapped(line, reg, FONT_SIZE);
        }
        w.moveDown(SECTION_GAP);
    }

    private void renderPdfCustomSection(PdfWriter w, PDType0Font reg, PDType0Font bold,
                                        String label, JsonNode content) throws Exception {
        if (content==null || content.isMissingNode()) return;
        String mode  = content.path("mode").asText("text");
        String text  = content.path("text").asText("").trim();
        JsonNode items = content.path("items");
        if ("bullets".equals(mode)) {
            if (!items.isArray()) return;
            List<String> blist = new ArrayList<>();
            for (JsonNode it : items) { String v=it.asText("").trim(); if(!v.isBlank()) blist.add(v); }
            if (blist.isEmpty()) return;
            w.writeHeading(label.toUpperCase());
            for (String b : blist) w.writeBullet(b, reg, FONT_SIZE);
            w.moveDown(SECTION_GAP);
        } else {
            if (text.isBlank()) return;
            w.writeHeading(label.toUpperCase());
            w.writeWrapped(text, reg, FONT_SIZE);
            w.moveDown(SECTION_GAP);
        }
    }

    /* ================================================================
       DOCX EXPORT
       ================================================================ */

    public byte[] exportToDocx(User user, Long resumeId) {
        Resume resume = load(user, resumeId);
        try (XWPFDocument doc = new XWPFDocument()) {
            CTSectPr sp = doc.getDocument().getBody().addNewSectPr();
            CTPageMar pm = sp.addNewPgMar();
            pm.setLeft(BigInteger.valueOf(1080)); pm.setRight(BigInteger.valueOf(1080));
            pm.setTop(BigInteger.valueOf(1080));  pm.setBottom(BigInteger.valueOf(1080));

            /* ── Header ─────────────────────────────────────────── */
            JsonNode pi = parseObj(resume.getPersonalInfo());
            dName(doc, or(t(pi,"fullName"),"Your Name"));
            if (!t(pi,"professionalTitle").isBlank()) dSubtitle(doc, t(pi,"professionalTitle"));
            List<String> contact = parts(t(pi,"email"),t(pi,"phone"),t(pi,"location"));
            if (!contact.isEmpty()) dMeta(doc, join(contact,"   |   "));
            List<String> links = parts(t(pi,"linkedin"),t(pi,"github"),t(pi,"portfolio"));
            if (!links.isEmpty()) dMeta(doc, join(links,"   |   "));

            /* ── Ordered sections ───────────────────────────────── */
            List<JsonNode> sections = orderedVisible(resume.getSectionsConfig());
            JsonNode customSections = parseObj(resume.getCustomSections());

            for (JsonNode sec : sections) {
                String stype = t(sec,"type"), key=t(sec,"key"), label=t(sec,"label");
                if ("custom".equals(stype)) {
                    docxCustomSection(doc, label, customSections.path(t(sec,"id"))); continue;
                }
                switch (key) {
                    case "basics": break;
                    case "summary":
                        if (resume.getSummary()!=null && !resume.getSummary().isBlank()) {
                            dHeading(doc, hl(label,"PROFESSIONAL SUMMARY"));
                            dBody(doc, resume.getSummary());
                        } break;
                    case "experience":
                        docxExperience(doc, label, parseArr(resume.getExperience())); break;
                    case "projects":
                        docxProjects(doc, label, parseArr(resume.getProjects())); break;
                    case "education":
                        docxEducation(doc, label, parseArr(resume.getEducation())); break;
                    case "skills":
                        docxSkills(doc, label, parseArr(resume.getSkills())); break;
                    case "achievements":
                        docxList(doc, label, parseArr(resume.getAchievements())); break;
                    case "languages":
                        docxSkills(doc, label, parseArr(resume.getLanguages())); break;
                    case "certifications":
                        docxCertifications(doc, label, parseArr(resume.getCertifications())); break;
                    default:
                        docxCustomSection(doc, label, customSections.path(key)); break;
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        } catch (ResourceNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate DOCX: " + e.getMessage(), e);
        }
    }

    /* ── DOCX section renderers ─────────────────────────────────── */

    private void docxExperience(XWPFDocument doc, String label, JsonNode arr) {
        if (!arr.isArray()||arr.size()==0) return;
        dHeading(doc, hl(label,"PROFESSIONAL EXPERIENCE"));
        for (JsonNode e : arr) {
            String role=t(e,"role"),company=t(e,"company"),dates=dateRange(e);
            String head=role.isBlank()?company:company.isBlank()?role:role+" — "+company;
            if (!head.isBlank()) dTwoCol(doc, head, dates);
            String meta=joinMeta(t(e,"location"),t(e,"employmentType")); if(!meta.isBlank()) dMeta(doc,meta);
            if (!t(e,"summary").isBlank()) dBody(doc, t(e,"summary"));
            for (JsonNode b : e.path("bullets")) { String bl=b.asText(""); if(!bl.isBlank()) dBullet(doc,bl); }
            dSpacer(doc);
        }
    }

    private void docxProjects(XWPFDocument doc, String label, JsonNode arr) {
        if (!arr.isArray()||arr.size()==0) return;
        dHeading(doc, hl(label,"PROJECTS"));
        for (JsonNode p : arr) {
            String name=t(p,"name"),role=t(p,"role");
            String head=role.isBlank()?name:name+"("+role+")";
            if (!head.isBlank()) dBold(doc,head);
            if (!t(p,"techStack").isBlank()) dMeta(doc,"Tech: "+t(p,"techStack"));
            List<String> urls=parts(t(p,"link"),t(p,"github")); if(!urls.isEmpty()) dMeta(doc,join(urls,"  ·  "));
            if (!t(p,"description").isBlank()) dBody(doc,t(p,"description"));
            for (JsonNode h : p.path("highlights")) { String hl=h.asText(""); if(!hl.isBlank()) dBullet(doc,hl); }
            dSpacer(doc);
        }
    }

    private void docxEducation(XWPFDocument doc, String label, JsonNode arr) {
        if (!arr.isArray()||arr.size()==0) return;
        dHeading(doc, hl(label,"EDUCATION"));
        for (JsonNode e : arr) {
            String degree=t(e,"degree"),field=t(e,"field"),dates=dateRange(e);
            String head=field.isBlank()?degree:degree+" in "+field;
            if (!head.isBlank()) dTwoCol(doc,head,dates);
            if (!t(e,"institution").isBlank()) dBody(doc,t(e,"institution"));
            if (!t(e,"grade").isBlank()) dMeta(doc,"Grade: "+t(e,"grade"));
            if (!t(e,"details").isBlank()) dMeta(doc,t(e,"details"));
            dSpacer(doc);
        }
    }

    private void docxSkills(XWPFDocument doc, String label, JsonNode arr) {
        if (!arr.isArray()||arr.size()==0) return;
        dHeading(doc, hl(label,"SKILLS"));
        List<String> list=new ArrayList<>();
        for (JsonNode s:arr){String v=s.asText(""); if(!v.isBlank())list.add(v);}
        if (!list.isEmpty()) dBody(doc, join(list,"  ·  "));
    }

    private void docxList(XWPFDocument doc, String label, JsonNode arr) {
        if (!arr.isArray()||arr.size()==0) return;
        dHeading(doc, hl(label,"ACHIEVEMENTS"));
        for (JsonNode a:arr){String v=a.asText(""); if(!v.isBlank()) dBullet(doc,v);}
    }

    private void docxCertifications(XWPFDocument doc, String label, JsonNode arr) {
        if (!arr.isArray()||arr.size()==0) return;
        dHeading(doc, hl(label,"CERTIFICATIONS"));
        for (JsonNode c:arr){
            String line=c.isTextual()?c.asText(""):certLine(c);
            if (!line.isBlank()) dBody(doc,line);
        }
    }

    private void docxCustomSection(XWPFDocument doc, String label, JsonNode content) {
        if (content==null||content.isMissingNode()) return;
        String mode=content.path("mode").asText("text");
        if ("bullets".equals(mode)) {
            JsonNode items=content.path("items");
            if (!items.isArray()) return;
            List<String> list=new ArrayList<>();
            for (JsonNode it:items){String v=it.asText("").trim(); if(!v.isBlank()) list.add(v);}
            if (list.isEmpty()) return;
            dHeading(doc, label.toUpperCase());
            list.forEach(b -> dBullet(doc, b));
        } else {
            String text=content.path("text").asText("").trim();
            if (text.isBlank()) return;
            dHeading(doc, label.toUpperCase());
            dBody(doc, text);
        }
    }

    /* ================================================================
       TXT EXPORT
       ================================================================ */

    public byte[] exportToTxt(User user, Long resumeId) {
        Resume resume = load(user, resumeId);
        StringBuilder t = new StringBuilder();

        JsonNode pi = parseObj(resume.getPersonalInfo());
        t.append(or(this.t(pi,"fullName"),"Your Name")).append("\n");
        if (!this.t(pi,"professionalTitle").isBlank()) t.append(this.t(pi,"professionalTitle")).append("\n");
        List<String> contact=parts(this.t(pi,"email"),this.t(pi,"phone"),this.t(pi,"location"));
        if (!contact.isEmpty()) t.append(join(contact,"  |  ")).append("\n");
        List<String> links=parts(this.t(pi,"linkedin"),this.t(pi,"github"),this.t(pi,"portfolio"));
        if (!links.isEmpty()) t.append(join(links,"  |  ")).append("\n");
        t.append("\n");

        List<JsonNode> sections = orderedVisible(resume.getSectionsConfig());
        JsonNode customSections = parseObj(resume.getCustomSections());

        for (JsonNode sec : sections) {
            String stype=this.t(sec,"type"), key=this.t(sec,"key"), label=this.t(sec,"label");
            if ("custom".equals(stype)) {
                txtCustomSection(t, label, customSections.path(this.t(sec,"id"))); continue;
            }
            switch (key) {
                case "basics": break;
                case "summary":
                    if (resume.getSummary()!=null&&!resume.getSummary().isBlank()) {
                        txtHeading(t, hl(label,"PROFESSIONAL SUMMARY"));
                        t.append(resume.getSummary()).append("\n\n");
                    } break;
                case "experience":
                    txtExperience(t, label, parseArr(resume.getExperience())); break;
                case "projects":
                    txtProjects(t, label, parseArr(resume.getProjects())); break;
                case "education":
                    txtEducation(t, label, parseArr(resume.getEducation())); break;
                case "skills":
                    txtSkills(t, label, parseArr(resume.getSkills())); break;
                case "achievements":
                    txtList(t, label, parseArr(resume.getAchievements())); break;
                case "languages":
                    txtSkills(t, label, parseArr(resume.getLanguages())); break;
                case "certifications":
                    txtCertifications(t, label, parseArr(resume.getCertifications())); break;
                default:
                    txtCustomSection(t, label, customSections.path(key)); break;
            }
        }

        return t.toString().getBytes(StandardCharsets.UTF_8);
    }

    /* ── TXT section renderers ──────────────────────────────────── */

    private void txtExperience(StringBuilder t, String label, JsonNode arr) {
        if (!arr.isArray()||arr.size()==0) return;
        txtHeading(t, hl(label,"PROFESSIONAL EXPERIENCE"));
        for (JsonNode e:arr) {
            String role=this.t(e,"role"),company=this.t(e,"company"),dates=dateRange(e);
            String head=role.isBlank()?company:company.isBlank()?role:role+" — "+company;
            if (!head.isBlank()){t.append(head); if(!dates.isBlank())t.append("  (").append(dates).append(")"); t.append("\n");}
            String meta=joinMeta(this.t(e,"location"),this.t(e,"employmentType")); if(!meta.isBlank()) t.append(meta).append("\n");
            if (!this.t(e,"summary").isBlank()) t.append(this.t(e,"summary")).append("\n");
            for (JsonNode b:e.path("bullets")){String bl=b.asText(""); if(!bl.isBlank()) t.append("  - ").append(bl).append("\n");}
            t.append("\n");
        }
    }

    private void txtProjects(StringBuilder t, String label, JsonNode arr) {
        if (!arr.isArray()||arr.size()==0) return;
        txtHeading(t, hl(label,"PROJECTS"));
        for (JsonNode p:arr) {
            String name=this.t(p,"name"),role=this.t(p,"role");
            String head=role.isBlank()?name:name+"("+role+")"; if(!head.isBlank()) t.append(head).append("\n");
            if (!this.t(p,"techStack").isBlank()) t.append("Tech: ").append(this.t(p,"techStack")).append("\n");
            List<String> urls=parts(this.t(p,"link"),this.t(p,"github")); if(!urls.isEmpty()) t.append(join(urls,"  ·  ")).append("\n");
            if (!this.t(p,"description").isBlank()) t.append(this.t(p,"description")).append("\n");
            for (JsonNode h:p.path("highlights")){String hl=h.asText(""); if(!hl.isBlank()) t.append("  - ").append(hl).append("\n");}
            t.append("\n");
        }
    }

    private void txtEducation(StringBuilder t, String label, JsonNode arr) {
        if (!arr.isArray()||arr.size()==0) return;
        txtHeading(t, hl(label,"EDUCATION"));
        for (JsonNode e:arr) {
            String degree=this.t(e,"degree"),field=this.t(e,"field"),dates=dateRange(e);
            String head=field.isBlank()?degree:degree+" in "+field;
            if (!head.isBlank()){t.append(head); if(!dates.isBlank())t.append("  (").append(dates).append(")"); t.append("\n");}
            if (!this.t(e,"institution").isBlank()) t.append(this.t(e,"institution")).append("\n");
            if (!this.t(e,"grade").isBlank()) t.append("Grade: ").append(this.t(e,"grade")).append("\n");
            if (!this.t(e,"details").isBlank()) t.append(this.t(e,"details")).append("\n");
            t.append("\n");
        }
    }

    private void txtSkills(StringBuilder t, String label, JsonNode arr) {
        if (!arr.isArray()||arr.size()==0) return;
        txtHeading(t, hl(label,"SKILLS"));
        List<String> list=new ArrayList<>();
        for (JsonNode s:arr){String v=s.asText(""); if(!v.isBlank()) list.add(v);}
        if (!list.isEmpty()) t.append(join(list,"  ·  ")).append("\n\n");
    }

    private void txtList(StringBuilder t, String label, JsonNode arr) {
        if (!arr.isArray()||arr.size()==0) return;
        txtHeading(t, hl(label,"ACHIEVEMENTS"));
        for (JsonNode a:arr){String v=a.asText(""); if(!v.isBlank()) t.append("  - ").append(v).append("\n");}
        t.append("\n");
    }

    private void txtCertifications(StringBuilder t, String label, JsonNode arr) {
        if (!arr.isArray()||arr.size()==0) return;
        txtHeading(t, hl(label,"CERTIFICATIONS"));
        for (JsonNode c:arr){String line=c.isTextual()?c.asText(""):certLine(c); if(!line.isBlank()) t.append(line).append("\n");}
        t.append("\n");
    }

    private void txtCustomSection(StringBuilder t, String label, JsonNode content) {
        if (content==null||content.isMissingNode()) return;
        String mode=content.path("mode").asText("text");
        if ("bullets".equals(mode)) {
            JsonNode items=content.path("items"); if(!items.isArray()) return;
            List<String> list=new ArrayList<>();
            for (JsonNode it:items){String v=it.asText("").trim(); if(!v.isBlank()) list.add(v);}
            if (list.isEmpty()) return;
            txtHeading(t, label.toUpperCase());
            list.forEach(b -> t.append("  - ").append(b).append("\n"));
            t.append("\n");
        } else {
            String text=content.path("text").asText("").trim(); if(text.isBlank()) return;
            txtHeading(t, label.toUpperCase());
            t.append(text).append("\n\n");
        }
    }

    private void txtHeading(StringBuilder t, String h) {
        t.append(h).append("\n").append("-".repeat(Math.min(h.length(),60))).append("\n");
    }

    /* ================================================================
       sectionsConfig resolver — returns ordered visible sections.
       Falls back to a hard-coded default order when sectionsConfig is
       null/blank (existing resumes before V20 migration).
       ================================================================ */

    private static final String DEFAULT_ORDER =
            "[{\"type\":\"standard\",\"key\":\"basics\",\"id\":\"basics\",\"label\":\"Personal Info\",\"visible\":true}," +
                    "{\"type\":\"standard\",\"key\":\"summary\",\"id\":\"summary\",\"label\":\"Summary\",\"visible\":true}," +
                    "{\"type\":\"standard\",\"key\":\"skills\",\"id\":\"skills\",\"label\":\"Skills\",\"visible\":true}," +
                    "{\"type\":\"standard\",\"key\":\"experience\",\"id\":\"experience\",\"label\":\"Experience\",\"visible\":true}," +
                    "{\"type\":\"standard\",\"key\":\"projects\",\"id\":\"projects\",\"label\":\"Projects\",\"visible\":true}," +
                    "{\"type\":\"standard\",\"key\":\"education\",\"id\":\"education\",\"label\":\"Education\",\"visible\":true}," +
                    "{\"type\":\"standard\",\"key\":\"certifications\",\"id\":\"certifications\",\"label\":\"Certifications\",\"visible\":true}," +
                    "{\"type\":\"standard\",\"key\":\"achievements\",\"id\":\"achievements\",\"label\":\"Achievements\",\"visible\":true}," +
                    "{\"type\":\"standard\",\"key\":\"languages\",\"id\":\"languages\",\"label\":\"Languages\",\"visible\":true}]";

    private List<JsonNode> orderedVisible(String sectionsConfigJson) {
        JsonNode arr = parseArr(sectionsConfigJson==null||sectionsConfigJson.isBlank() ? DEFAULT_ORDER : sectionsConfigJson);
        return StreamSupport.stream(arr.spliterator(), false)
                .filter(s -> s.path("visible").asBoolean(true))
                .collect(Collectors.toList());
    }

    /* ================================================================
       JSON helpers
       ================================================================ */

    private JsonNode parseObj(String json) {
        if (json==null||json.isBlank()) return om.createObjectNode();
        try { JsonNode n=om.readTree(json); return n.isObject()?n:om.createObjectNode(); } catch(Exception e){return om.createObjectNode();}
    }

    private JsonNode parseArr(String json) {
        if (json==null||json.isBlank()) return om.createArrayNode();
        try { JsonNode n=om.readTree(json); return n.isArray()?n:om.createArrayNode(); } catch(Exception e){return om.createArrayNode();}
    }

    private String t(JsonNode n, String field) {
        if (n==null||!n.hasNonNull(field)) return "";
        String v=n.get(field).asText(""); return v==null?"":v.trim();
    }

    private String dateRange(JsonNode n) {
        String s=t(n,"startDate"), e=t(n,"endDate");
        if (s.isBlank()&&e.isBlank()) return "";
        if (s.isBlank()) return e; if (e.isBlank()) return s; return s+" – "+e;
    }

    private String certLine(JsonNode c) {
        String name=t(c,"name"),issuer=t(c,"issuer"),year=t(c,"year");
        StringBuilder sb=new StringBuilder(name);
        if(!issuer.isBlank()) sb.append(" — ").append(issuer);
        if(!year.isBlank()) sb.append(" (").append(year).append(")");
        return sb.toString();
    }

    private List<String> parts(String... vals) {
        List<String> r=new ArrayList<>();
        for (String v:vals) if(v!=null&&!v.isBlank()) r.add(v);
        return r;
    }

    private String join(List<String> list, String sep) { return String.join(sep, list); }

    private String joinMeta(String loc, String type) {
        if(loc.isBlank()&&type.isBlank()) return "";
        if(loc.isBlank()) return type; if(type.isBlank()) return loc;
        return loc+"  ·  "+type;
    }

    private String or(String a, String b) { return (a==null||a.isBlank())?b:a; }

    /** Use the user-specified section label if non-blank, otherwise fall back to the default heading string. */
    private String hl(String label, String fallback) {
        return (label==null||label.isBlank()) ? fallback : label.toUpperCase();
    }

    /* ================================================================
       PdfWriter — multi-page inner class
       ================================================================ */

    private class PdfWriter {
        private final PDDocument  doc;
        private final PDType0Font reg, bold;
        private PDPage page;
        private PDPageContentStream cs;
        private float y;

        PdfWriter(PDDocument doc, PDType0Font reg, PDType0Font bold) throws Exception {
            this.doc=doc; this.reg=reg; this.bold=bold; newPage();
        }

        private void newPage() throws Exception {
            if (cs!=null) cs.close();
            page=new PDPage(PDRectangle.A4); doc.addPage(page);
            cs=new PDPageContentStream(doc, page); y=PAGE_H-MARGIN;
        }

        private void ensure(float need) throws Exception { if(y-need<BOTTOM_MARGIN) newPage(); }

        void moveDown(float d) { y-=d; }

        void writeHeading(String text) throws Exception {
            ensure(HEADING_SIZE+HEADING_GAP+LINE_H);
            String s=san(text, bold);
            cs.beginText(); cs.setFont(bold, HEADING_SIZE); cs.newLineAtOffset(MARGIN,y); cs.showText(s); cs.endText();
            y-=2f;
            cs.setLineWidth(0.75f); cs.moveTo(MARGIN,y); cs.lineTo(PAGE_W-MARGIN,y); cs.stroke();
            y-=(HEADING_GAP+4f);
        }

        void writeLine(String text, PDType0Font font, float size) throws Exception {
            ensure(size+LINE_H);
            cs.beginText(); cs.setFont(font,size); cs.newLineAtOffset(MARGIN,y); cs.showText(san(text,font)); cs.endText();
            y-=(size+LINE_H-size*0.4f);
        }

        void writeTwoCol(String left, String right, PDType0Font font, float size) throws Exception {
            ensure(size+LINE_H);
            String sl=san(left,font), sr=san(right==null?"":right, reg);
            cs.beginText(); cs.setFont(font,size); cs.newLineAtOffset(MARGIN,y); cs.showText(sl); cs.endText();
            if (!sr.isBlank()) {
                float rw=reg.getStringWidth(sr)/1000*SMALL_SIZE;
                float rx=PAGE_W-MARGIN-rw;
                cs.beginText(); cs.setFont(reg,SMALL_SIZE); cs.newLineAtOffset(rx,y+(size-SMALL_SIZE)*0.3f); cs.showText(sr); cs.endText();
            }
            y-=(size+LINE_H-size*0.4f);
        }

        void writeBullet(String text, PDType0Font font, float size) throws Exception {
            if (text==null||text.isBlank()) return;
            float indent=12f;
            List<String> lines=wrap(san(sanitize(text),font), font, size, USABLE_W-indent);
            boolean first=true;
            for (String line:lines) {
                ensure(size+LINE_H);
                cs.beginText(); cs.setFont(font,size);
                cs.newLineAtOffset(MARGIN+(first?0:indent),y);
                cs.showText((first?"-  ":"")+line); cs.endText();
                y-=(size+LINE_H-size*0.4f); first=false;
            }
        }

        void writeWrapped(String text, PDType0Font font, float size) throws Exception {
            if (text==null||text.isBlank()) return;
            for (String line:wrap(san(sanitize(text),font),font,size,USABLE_W)) {
                ensure(size+LINE_H);
                cs.beginText(); cs.setFont(font,size); cs.newLineAtOffset(MARGIN,y); cs.showText(line); cs.endText();
                y-=(size+LINE_H-size*0.4f);
            }
        }

        void close() throws Exception { if(cs!=null) cs.close(); }

        private List<String> wrap(String text, PDType0Font font, float size, float maxW) throws Exception {
            List<String> lines=new ArrayList<>();
            if(text==null||text.isBlank()) return lines;
            String[] words=text.split("\\s+"); StringBuilder cur=new StringBuilder();
            for (String word:words) {
                String cand=cur.isEmpty()?word:cur+" "+word;
                if (font.getStringWidth(cand)/1000*size<=maxW) { cur=new StringBuilder(cand); }
                else { if(!cur.isEmpty()) lines.add(cur.toString()); cur=new StringBuilder(word); }
            }
            if(!cur.isEmpty()) lines.add(cur.toString());
            return lines;
        }

        private String san(String text, PDType0Font font) {
            if(text==null||text.isEmpty()) return "";
            String n=text.replace('\u2013','-').replace('\u2014','-').replace('\u2018','\'')
                    .replace('\u2019','\'').replace('\u201c','"').replace('\u201d','"')
                    .replace('\u2026',' ').replace('\u00a0',' ').replace('\u2022','-');
            StringBuilder sb=new StringBuilder(n.length());
            for (int i=0;i<n.length();) {
                int cp=n.codePointAt(i);
                boolean ok; try{ok=font.getWidth(cp)>0;}catch(Exception e){ok=false;}
                if(ok||cp<128) sb.appendCodePoint(cp); else sb.append('?');
                i+=Character.charCount(cp);
            }
            return sb.toString();
        }

        private String sanitize(String text) {
            if(text==null) return "";
            return text.replaceAll("[\\p{Cntrl}&&[^\\n]]"," ").replace("\n"," ").trim();
        }
    }

    /* ================================================================
       DOCX helpers
       ================================================================ */

    private void dName(XWPFDocument d, String t)    { XWPFParagraph p=d.createParagraph(); p.setSpacingAfter(40); XWPFRun r=p.createRun(); r.setText(t); r.setBold(true); r.setFontSize(22); }
    private void dSubtitle(XWPFDocument d, String t){ XWPFParagraph p=d.createParagraph(); p.setSpacingAfter(40); XWPFRun r=p.createRun(); r.setText(t); r.setFontSize(12); r.setColor("444444"); }
    private void dMeta(XWPFDocument d, String t)    { if(t==null||t.isBlank()) return; XWPFParagraph p=d.createParagraph(); p.setSpacingAfter(20); XWPFRun r=p.createRun(); r.setText(t); r.setFontSize(9); r.setColor("666666"); }
    private void dHeading(XWPFDocument d, String t) { XWPFParagraph p=d.createParagraph(); p.setSpacingBefore(220); p.setSpacingAfter(80); p.setBorderBottom(Borders.SINGLE); XWPFRun r=p.createRun(); r.setText(t); r.setBold(true); r.setFontSize(12); }
    private void dBold(XWPFDocument d, String t)    { if(t==null||t.isBlank()) return; XWPFParagraph p=d.createParagraph(); p.setSpacingAfter(20); XWPFRun r=p.createRun(); r.setText(t); r.setBold(true); r.setFontSize(11); }
    private void dBody(XWPFDocument d, String t)    { if(t==null||t.isBlank()) return; XWPFParagraph p=d.createParagraph(); p.setSpacingAfter(60); XWPFRun r=p.createRun(); r.setText(t); r.setFontSize(10); }
    private void dBullet(XWPFDocument d, String t)  { if(t==null||t.isBlank()) return; XWPFParagraph p=d.createParagraph(); p.setIndentationLeft(260); p.setSpacingAfter(20); XWPFRun r=p.createRun(); r.setText("•  "+t); r.setFontSize(10); }
    private void dSpacer(XWPFDocument d)             { d.createParagraph().setSpacingAfter(40); }
    private void dTwoCol(XWPFDocument d, String l, String r) {
        if(l==null||l.isBlank()) return;
        XWPFParagraph p=d.createParagraph(); p.setSpacingAfter(20);
        XWPFRun rl=p.createRun(); rl.setText(l); rl.setBold(true); rl.setFontSize(11);
        if(r!=null&&!r.isBlank()){XWPFRun rr=p.createRun(); rr.setText("   ("+r+")"); rr.setFontSize(9); rr.setColor("666666");}
    }

    /* ================================================================
       Misc helpers
       ================================================================ */

    private InputStream font(String path) {
        InputStream s=getClass().getResourceAsStream(path);
        if(s==null) throw new IllegalStateException("Font missing from classpath: "+path);
        return s;
    }

    private Resume load(User user, Long id) {
        return resumeRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));
    }

    private long countRecent(User user) {
        return exportHistoryRepository.countRecentExports(user.getId(), LocalDateTime.now().minusDays(1));
    }

    private String sanitizeFilename(String title) {
        if(title==null||title.isBlank()) return "resume";
        String s=title.trim().replaceAll("[^a-zA-Z0-9\\-]","_").replaceAll("_+","_").replaceAll("^_|_$","");
        return s.isEmpty()?"resume":s;
    }

    private ExportHistoryResponse toHistoryResponse(ExportHistory e) {
        return ExportHistoryResponse.builder().id(e.getId()).resumeId(e.getResumeId())
                .exportFormat(e.getExportFormat()).createdAt(e.getCreatedAt()).build();
    }
}