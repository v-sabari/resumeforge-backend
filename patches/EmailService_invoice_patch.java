// ── EmailService patch ───────────────────────────────────────────────────────
//
// Add this method to the existing EmailService.java class.
// It sends the PDF invoice as a base64 attachment via Resend's attachment API.
//
// Resend supports attachments in this format:
//   "attachments": [{ "filename": "...", "content": "<base64>" }]
//

    public void sendInvoiceEmail(String toEmail, String customerName,
                                  String paymentId, byte[] pdfBytes) {
        String subject = "Your ResumeForge AI Premium receipt";
        String html = """
                <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #111827;">
                  <h2 style="margin-bottom: 8px;">You're Premium!</h2>
                  <p>Hi %s,</p>
                  <p>Thank you for upgrading to <strong>ResumeForge AI Premium</strong>.
                     Your account now has unlimited exports, all AI tools, and all templates.</p>
                  <p>Your payment receipt is attached to this email (PDF).</p>
                  <p style="margin-top: 20px;">
                    <a href="https://www.resumeforgeai.site/app/dashboard"
                       style="background:#2563eb;color:#fff;padding:10px 20px;
                              border-radius:8px;text-decoration:none;font-weight:600;">
                      Go to my dashboard
                    </a>
                  </p>
                  <p style="margin-top: 24px; color: #6b7280; font-size: 13px;">
                    Need help? Reply to this email or contact support@resumeforgeai.site
                  </p>
                </div>
                """.formatted(escapeHtml(customerName));

        String base64Pdf = java.util.Base64.getEncoder().encodeToString(pdfBytes);
        String filename  = "resumeforgeai_receipt_" + paymentId + ".pdf";

        // Resend API with attachment
        try {
            java.net.URL url = new java.net.URL("https://api.resend.com/emails");
            java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + resendApiKey);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String json = """
                    {
                      "from": "%s <%s>",
                      "to": ["%s"],
                      "subject": "%s",
                      "html": %s,
                      "attachments": [
                        { "filename": "%s", "content": "%s" }
                      ]
                    }
                    """.formatted(
                    escapeJson(fromName), escapeJson(fromEmail),
                    escapeJson(toEmail),
                    escapeJson(subject),
                    toJsonString(html),
                    escapeJson(filename),
                    base64Pdf
            );

            try (java.io.OutputStream os = conn.getOutputStream()) {
                os.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new RuntimeException("Resend API returned HTTP " + status);
            }
        } catch (Exception e) {
            throw new com.resumeforge.ai.exception.ApiException(
                    org.springframework.http.HttpStatus.BAD_GATEWAY, "Could not send invoice email");
        }
    }

// ── PaymentService integration ────────────────────────────────────────────────
//
// Inject InvoiceService into PaymentService (constructor injection).
// Then add this call at the end of grantPremium():
//
//   private void grantPremium(User user) {
//       if (!user.isPremium()) {
//           user.setPremium(true);
//           userRepository.save(user);
//       }
//   }
//
// And in both verify() and handlePaymentCaptured(), AFTER grantPremium() succeeds:
//
//   // Send invoice email (errors caught inside sendInvoiceEmail — never blocks)
//   invoiceService.sendInvoiceEmail(payment, user);
//
// This means the invoice is sent both via the client-side callback verify
// AND via the webhook — but InvoiceService.sendInvoiceEmail() is idempotent
// (it just sends an email, doesn't check for duplicates), so the user may
// occasionally receive two receipts. At this scale that's acceptable.
// To deduplicate: check if a receipt was already sent by storing a flag
// on the Payment entity (e.g. Payment.invoiceSent boolean column).
