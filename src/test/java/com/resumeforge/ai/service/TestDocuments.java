package com.resumeforge.ai.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Builds real in-memory documents (PDF/DOCX) for the parsing tests. PDFBox and
 * POI generate genuine files here — the tests exercise the same code path as a
 * real upload rather than mocking the extraction result.
 */
final class TestDocuments {

    private TestDocuments() {
    }

    /** A single-page PDF with one line of text per entry. */
    static byte[] singlePagePdf(String... lines) throws IOException {
        return pagesPdf(List.of(List.of(lines)));
    }

    /** A multi-page PDF; each inner list becomes one page. */
    static byte[] pagesPdf(List<List<String>> pages) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDFont font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (List<String> lines : pages) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                doc.addPage(page);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.setFont(font, 12);
                    cs.beginText();
                    cs.newLineAtOffset(72, 720);
                    for (String line : lines) {
                        cs.showText(line);
                        cs.newLineAtOffset(0, -16);
                    }
                    cs.endText();
                }
            }
            return save(doc);
        }
    }

    /** A valid PDF whose only page has no content stream (no extractable text). */
    static byte[] emptyPdf() throws IOException {
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage(PDRectangle.LETTER));
            return save(doc);
        }
    }

    /** A PDF using an embedded Unicode (TrueType) font. */
    static byte[] unicodePdf(String line) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (InputStream in = TestDocuments.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
                if (in == null) {
                    throw new IllegalStateException("Test font /fonts/DejaVuSans.ttf not found on classpath");
                }
                PDFont font = PDType0Font.load(doc, in);
                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    cs.setFont(font, 14);
                    cs.beginText();
                    cs.newLineAtOffset(72, 700);
                    cs.showText(line);
                    cs.endText();
                }
            }
            return save(doc);
        }
    }

    /** A PDF encrypted with a non-empty USER password (cannot be opened blank). */
    static byte[] encryptedPdf(String userPassword) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                cs.beginText();
                cs.newLineAtOffset(72, 700);
                cs.showText("Secret resume content");
                cs.endText();
            }
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy("owner-pass", "user-pass", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            return save(doc);
        }
    }

    /** Bytes that claim to be a PDF but are structurally broken. */
    static byte[] corruptedPdf() {
        return "%PDF-1.7\nnot a real pdf body\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }

    /** A DOCX with one paragraph per entry. */
    static byte[] docx(String... paragraphs) throws IOException {
        try (XWPFDocument doc = new XWPFDocument()) {
            for (String p : paragraphs) {
                XWPFParagraph para = doc.createParagraph();
                XWPFRun run = para.createRun();
                run.setText(p);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    private static byte[] save(PDDocument doc) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.save(out);
        return out.toByteArray();
    }
}
