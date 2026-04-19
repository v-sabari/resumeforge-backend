package com.resumeforge.ai.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.pdf.PdfWriter;
import com.resumeforge.ai.entity.Payment;
import com.resumeforge.ai.entity.User;
import com.resumeforge.ai.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Generates a PDF payment receipt/invoice using OpenPDF (already in pom.xml).
 *
 * Called from PaymentService.processWebhook() and PaymentService.verify()
 * after premium is successfully granted — sends the PDF to the user's email.
 *
 * The invoice contains:
 *   - ResumeForge AI branding
 *   - Invoice number (internal payment ID)
 *   - Date, customer name and email
 *   - Line item: "ResumeForge AI Premium — Lifetime Access"
 *   - Amount paid
 *   - Thank you note
 */
@Service
public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy").withZone(ZoneId.of("UTC"));

    private final EmailService emailService;

    public InvoiceService(EmailService emailService) {
        this.emailService = emailService;
    }

    /**
     * Generates the invoice PDF bytes for a payment.
     * Exposed as a public method so it can also be called from the
     * GET /api/payments/{id}/invoice endpoint (future).
     */
    public byte[] generateInvoicePdf(Payment payment, User user) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document doc = new Document(PageSize.A4, 60, 60, 60, 60);
            PdfWriter.getInstance(doc, out);
            doc.open();

            Font brandFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, new Color(37, 99, 235));
            Font headFont   = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);
            Font bodyFont   = FontFactory.getFont(FontFactory.HELVETICA, 11);
            Font mutedFont  = FontFactory.getFont(FontFactory.HELVETICA, 10, new Color(107, 114, 128));
            Font totalFont  = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13);

            // ── Brand header ────────────────────────────────────────────────
            Paragraph brand = new Paragraph("ResumeForge AI", brandFont);
            brand.setSpacingAfter(4);
            doc.add(brand);

            Paragraph tagline = new Paragraph("ATS-Optimised Resume Builder", mutedFont);
            tagline.setSpacingAfter(24);
            doc.add(tagline);

            // ── Horizontal rule ──────────────────────────────────────────────
            doc.add(new Chunk(new com.lowagie.text.pdf.draw.LineSeparator(
                    0.5f, 100, new Color(229, 231, 235), Element.ALIGN_LEFT, -4)));

            // ── Invoice meta ────────────────────────────────────────────────
            Paragraph invoiceTitle = new Paragraph("Payment Receipt", headFont);
            invoiceTitle.setSpacingBefore(20);
            invoiceTitle.setSpacingAfter(12);
            doc.add(invoiceTitle);

            String date = payment.getCapturedAt() != null
                    ? DATE_FMT.format(payment.getCapturedAt())
                    : DATE_FMT.format(payment.getCreatedAt());

            addRow(doc, "Invoice number:", payment.getPaymentId(),          bodyFont, mutedFont);
            addRow(doc, "Date:",           date,                            bodyFont, mutedFont);
            addRow(doc, "Customer name:",  user.getName(),                  bodyFont, mutedFont);
            addRow(doc, "Customer email:", user.getEmail(),                 bodyFont, mutedFont);
            if (payment.getRazorpayPaymentId() != null) {
                addRow(doc, "Razorpay ID:", payment.getRazorpayPaymentId(), bodyFont, mutedFont);
            }

            // ── Line items ──────────────────────────────────────────────────
            Paragraph itemsHead = new Paragraph("Items purchased", headFont);
            itemsHead.setSpacingBefore(24);
            itemsHead.setSpacingAfter(8);
            doc.add(itemsHead);

            doc.add(new Chunk(new com.lowagie.text.pdf.draw.LineSeparator(
                    0.5f, 100, new Color(229, 231, 235), Element.ALIGN_LEFT, -4)));

            addRow(doc, "ResumeForge AI Premium — Lifetime Access",
                    "₹" + payment.getAmount().toPlainString(),
                    bodyFont, bodyFont);

            doc.add(new Chunk(new com.lowagie.text.pdf.draw.LineSeparator(
                    0.5f, 100, new Color(229, 231, 235), Element.ALIGN_LEFT, 8)));

            // ── Total ────────────────────────────────────────────────────────
            Paragraph total = new Paragraph();
            total.add(new Chunk("Total paid:  ", bodyFont));
            total.add(new Chunk("₹" + payment.getAmount().toPlainString(), totalFont));
            total.setSpacingBefore(8);
            total.setSpacingAfter(24);
            doc.add(total);

            // ── Footer ───────────────────────────────────────────────────────
            Paragraph footer = new Paragraph(
                    "Thank you for choosing ResumeForge AI! Your Premium access is now active.\n" +
                    "Questions? Email us at support@resumeforgeai.site",
                    mutedFont);
            footer.setSpacingBefore(16);
            doc.add(footer);

            doc.close();
            return out.toByteArray();

        } catch (Exception e) {
            log.error("Invoice PDF generation failed for paymentId={}: {}",
                    payment.getPaymentId(), e.getMessage(), e);
            throw new RuntimeException("Invoice generation failed", e);
        }
    }

    /**
     * Generates the invoice and emails it to the user.
     * Call this after premium is confirmed.
     * Errors are caught and logged — never let invoice failure break the payment flow.
     */
    public void sendInvoiceEmail(Payment payment, User user) {
        try {
            byte[] pdf = generateInvoicePdf(payment, user);
            emailService.sendInvoiceEmail(user.getEmail(), user.getName(),
                    payment.getPaymentId(), pdf);
            log.info("Invoice emailed for paymentId={} userId={}", payment.getPaymentId(), user.getId());
        } catch (Exception e) {
            log.error("Invoice email failed for paymentId={}: {}", payment.getPaymentId(), e.getMessage(), e);
            // Do NOT rethrow — invoice failure must never rollback a payment
        }
    }

    private void addRow(Document doc, String label, String value,
                         Font labelFont, Font valueFont) throws DocumentException {
        Paragraph p = new Paragraph();
        p.add(new Chunk(label + "  ", labelFont));
        p.add(new Chunk(value, valueFont));
        p.setSpacingAfter(4);
        doc.add(p);
    }
}
