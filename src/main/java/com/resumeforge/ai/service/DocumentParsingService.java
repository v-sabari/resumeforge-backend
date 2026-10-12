package com.resumeforge.ai.service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * TOOL-01 / TOOL-02: real, deterministic document parsing shared by both new
 * free tools.
 *
 * <p>Reuses the libraries already on the classpath (Apache PDFBox for PDF,
 * Apache POI for DOCX) — no new dependency is introduced. Supported formats
 * mirror what the product already handles: PDF, DOCX and plain text. Legacy
 * binary {@code .doc} is NOT supported (POI's HWPF/scratchpad is not a
 * dependency) and is rejected with a clear message rather than pretending it
 * was parsed.</p>
 *
 * <p>Structural signals (image count, table count, multi-column suspicion,
 * header/footer text, garble ratio) are only reported when the parser can
 * actually inspect them. Anything not observable is left at its neutral value;
 * the Format Checker labels such items as a limitation rather than a confirmed
 * problem.</p>
 */
@Service
public class DocumentParsingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentParsingService.class);

    /** Hard cap on extracted characters kept in memory / sent back. */
    private static final int MAX_EXTRACTED_CHARS = 200_000;

    /**
     * Hard cap on the number of PDF pages whose text is extracted. A resume is
     * 1-2 pages; this bounds CPU/memory for a malformed or adversarial document
     * that claims a huge page count. {@code pageCount} is still reported as the
     * document's true page count.
     */
    private static final int MAX_PDF_PAGES_TO_EXTRACT = 50;

    private static final String MSG_PDF_PASSWORD =
            "This PDF is password-protected. Please upload an unlocked copy of your resume.";
    private static final String MSG_PDF_CORRUPT =
            "The PDF could not be read. It may be corrupted or invalid.";

    public ParsedDocument parse(byte[] bytes, String filename, String contentType) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("The uploaded file is empty.");
        }
        String type = detectType(bytes, filename, contentType);
        switch (type) {
            case "PDF":
                return parsePdf(bytes);
            case "DOCX":
                return parseDocx(bytes);
            case "TXT":
                return parseTxt(bytes);
            default:
                // "UNSUPPORTED:.doc" etc. — surface the extension to the caller.
                throw new IllegalArgumentException(type);
        }
    }

    /**
     * Resolves the concrete format from the filename extension first, then
     * magic bytes, then the declared content type. Never guesses: an unreadable
     * or unknown file resolves to an UNSUPPORTED marker.
     */
    private String detectType(byte[] bytes, String filename, String contentType) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        String ct = contentType == null ? "" : contentType.toLowerCase(Locale.ROOT);

        boolean pdfMagic = bytes.length >= 5
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
        boolean zipMagic = bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K';

        if (name.endsWith(".pdf") || ct.contains("pdf") || pdfMagic) return "PDF";
        if (name.endsWith(".docx") || ct.contains("officedocument.wordprocessingml") || zipMagic) {
            return "DOCX";
        }
        if (name.endsWith(".txt") || ct.startsWith("text/plain")) return "TXT";
        if (name.endsWith(".doc") || ct.contains("msword")) return "UNSUPPORTED:.doc";
        if (name.endsWith(".rtf") || ct.contains("rtf")) return "UNSUPPORTED:.rtf";
        if (name.endsWith(".pages")) return "UNSUPPORTED:.pages";
        return "UNSUPPORTED:" + (name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "unknown");
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    private ParsedDocument parsePdf(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            int pageCount = document.getNumberOfPages();

            PositionCollector stripper = new PositionCollector();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            if (pageCount > MAX_PDF_PAGES_TO_EXTRACT) {
                stripper.setEndPage(MAX_PDF_PAGES_TO_EXTRACT);
            }
            String text = stripper.getText(document);

            int imageCount = 0;
            for (PDPage page : document.getPages()) {
                imageCount += countPageImages(page);
            }

            boolean multiColumn = detectMultiColumn(stripper.xs, pageCount);
            return build(text, "PDF", pageCount, imageCount, 0, multiColumn, "", garbledPercent(text));
        } catch (InvalidPasswordException e) {
            // PDFBox raises this when the document is encrypted and no/empty
            // password unlocks it — a distinct, user-actionable case.
            log.info("[DocumentParsingService] Password-protected PDF rejected");
            throw new IllegalArgumentException(MSG_PDF_PASSWORD);
        } catch (IOException e) {
            // Malformed/truncated/invalid PDF stream.
            log.warn("[DocumentParsingService] PDF parse failed: {}", e.getMessage());
            throw new IllegalArgumentException(MSG_PDF_CORRUPT);
        } catch (RuntimeException e) {
            // Defensive: any unexpected PDFBox runtime failure is still a
            // user-facing "cannot read this PDF", never a 500 with a stack trace.
            log.warn("[DocumentParsingService] Unexpected PDF parse error: {}", e.getMessage());
            throw new IllegalArgumentException(MSG_PDF_CORRUPT);
        }
    }

    private int countPageImages(PDPage page) {
        try {
            var resources = page.getResources();
            if (resources == null) return 0;
            int n = 0;
            for (var name : resources.getXObjectNames()) {
                try {
                    if (resources.getXObject(name) instanceof PDImageXObject) n++;
                } catch (Exception ignored) {
                    // unresolvable XObject — don't count, don't fail
                }
            }
            return n;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Column heuristic: a page with a strong bimodal x-distribution (text on
     * the left and right with an empty central band) is treated as a suspected
     * multi-column layout. This is a RISK signal, not a confirmed error, and
     * only fires with a substantial sample of text positions.
     */
    private boolean detectMultiColumn(List<Float> xs, int pageCount) {
        if (xs.size() < 200 || pageCount == 0) return false;
        int left = 0, right = 0, middle = 0;
        for (float x : xs) {
            if (x < 0.40f) left++;
            else if (x > 0.60f) right++;
            else middle++;
        }
        int total = xs.size();
        return middle < total * 0.10 && left > total * 0.20 && right > total * 0.20;
    }

    /**
     * PDFTextStripper that records normalised x positions of every glyph.
     *
     * <p>ROOT-CAUSE FIX: {@link PDFTextStripper#writeString(String, List)} in
     * PDFBox is the method that actually appends the extracted line to the
     * stripper's output writer (it simply delegates to
     * {@code writeString(String)} → {@code output.write(text)}). The previous
     * version of this class overrode that method to collect x positions but
     * never called {@code super.writeString(...)}, so the extractor accumulated
     * nothing and {@link #getText} always returned an empty string for every
     * PDF — which is why every PDF, including FlowCV exports, was reported as
     * "no readable text". Calling {@code super} here writes the text to the
     * output while we still collect the x positions.
     */
    private static final class PositionCollector extends PDFTextStripper {
        final List<Float> xs = new ArrayList<>();

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            super.writeString(text, textPositions);
            for (TextPosition tp : textPositions) {
                float x = tp.getXDirAdj() / 600f;
                xs.add(Math.max(0f, Math.min(1f, x)));
            }
        }
    }

    // ── DOCX ──────────────────────────────────────────────────────────────────

    private ParsedDocument parseDocx(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder sb = new StringBuilder();
            int tableCount = 0;
            for (var element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph p) {
                    if (!p.getText().isBlank()) sb.append(p.getText()).append('\n');
                } else if (element instanceof XWPFTable t) {
                    tableCount++;
                    for (var row : t.getRows()) {
                        for (var cell : row.getTableCells()) {
                            if (!cell.getText().isBlank()) sb.append(cell.getText()).append(' ');
                        }
                        sb.append('\n');
                    }
                }
            }

            String text = sb.toString();
            int pageCount = estimateDocxPages(document);
            int imageCount = document.getAllPictures().size();

            StringBuilder hf = new StringBuilder();
            for (var header : document.getHeaderList()) {
                if (header.getText() != null) hf.append(header.getText()).append(' ');
            }
            for (var footer : document.getFooterList()) {
                if (footer.getText() != null) hf.append(footer.getText()).append(' ');
            }
            String headerFooter = hf.toString().trim();

            boolean multiColumn = docxHasColumns(document);
            return build(text, "DOCX", pageCount, imageCount, tableCount,
                    multiColumn, headerFooter, garbledPercent(text));
        } catch (Exception e) {
            log.warn("[DocumentParsingService] DOCX parse failed: {}", e.getMessage());
            throw new IllegalArgumentException("The DOCX could not be read. It may be corrupted.");
        }
    }

    private boolean docxHasColumns(XWPFDocument document) {
        try {
            var sectPr = document.getDocument().getBody().getSectPr();
            if (sectPr == null || sectPr.getCols() == null) return false;
            Object num = sectPr.getCols().getNum();
            if (num == null) return false;
            return Integer.parseInt(num.toString()) > 1;
        } catch (Exception e) {
            return false;
        }
    }

    /** DOCX has no fixed page count; approximate from explicit breaks. */
    private int estimateDocxPages(XWPFDocument document) {
        int breaks = 1;
        for (var p : document.getParagraphs()) {
            String t = p.getText();
            if (t != null && (t.contains("\f") || t.contains("\u000C"))) breaks++;
        }
        return Math.max(1, breaks);
    }

    // ── TXT ───────────────────────────────────────────────────────────────────

    private ParsedDocument parseTxt(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        return build(text, "TXT", 1, 0, 0, false, "", garbledPercent(text));
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    private ParsedDocument build(String rawText, String type, int pageCount,
                                 int imageCount, int tableCount,
                                 boolean multiColumn, String headerFooter, int garbled) {
        String cleaned = rawText == null ? "" : rawText
                .replace("\r\n", "\n")
                .replace('\r', '\n');
        if (cleaned.length() > MAX_EXTRACTED_CHARS) {
            cleaned = cleaned.substring(0, MAX_EXTRACTED_CHARS);
        }
        int chars = cleaned.length();
        int words = countWords(cleaned);
        return new ParsedDocument(cleaned, type, pageCount, chars, words,
                imageCount, tableCount, multiColumn, headerFooter == null ? "" : headerFooter, garbled);
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    /** Percentage of characters that look garbled (replacement chars / controls). */
    private int garbledPercent(String text) {
        if (text == null || text.isEmpty()) return 0;
        int bad = 0;
        int total = text.length();
        for (int i = 0; i < total; i++) {
            char c = text.charAt(i);
            if (c == '\uFFFD') {
                bad++;
            } else if (c < 0x20 && c != '\n' && c != '\r' && c != '\t') {
                bad++;
            }
        }
        return (int) Math.round((bad * 100.0) / total);
    }
}
