package com.resumeforge.ai.service;

import com.resumeforge.ai.entity.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
public class EmailService {

    @Value("${app.resend.api-key}")
    private String resendApiKey;

    @Value("${app.mail.from-email}")
    private String fromEmail;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.contact.receiver-email}")
    private String contactReceiverEmail;

    @Value("${app.frontend.base-url}")
    private String frontendBaseUrl;

    // FEEDBACK: dedicated recipient / sender so feedback mail can be routed
    // independently of the contact form. When unset they fall back to the
    // existing contact receiver / mail-from values (see sendFeedbackNotification).
    @Value("${app.feedback.to-email:}")
    private String feedbackToEmail;

    @Value("${app.feedback.from-email:}")
    private String feedbackFromEmail;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    public void sendVerificationEmail(String toEmail, String otp) {
        String subject = "Verify Your Email - ResumeForge AI";
        String html = buildVerificationEmailHtml(otp);
        sendEmail(toEmail, subject, html);
    }

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        String subject = "Reset Your Password - ResumeForge AI";
        // SEC FIX: build the reset link from configured frontend base URL instead
        // of a hardcoded production domain (breaks on custom domain / local dev).
        String resetLink = frontendBaseUrl.replaceAll("/+$", "") + "/reset-password?token=" + resetToken;
        String html = buildPasswordResetEmailHtml(resetLink);
        sendEmail(toEmail, subject, html);
    }

    public void sendInvoiceEmail(String toEmail, Payment payment) {
        String subject = "Payment Invoice - ResumeForge AI";
        String html = buildInvoiceEmailHtml(payment);
        sendEmail(toEmail, subject, html);
    }

    public void sendContactNotification(String name, String email, String message) {
        String subject = "New Contact Form Submission";
        String html = buildContactNotificationHtml(name, email, message);
        sendEmail(contactReceiverEmail, subject, html);
    }

    /**
     * Internal notification for a feedback submission (bug / feature / general).
     *
     * @return true when the email provider accepted the message, false otherwise.
     *         The caller persists the feedback regardless and surfaces a warning,
     *         because losing a report is worse than a failed notification.
     */
    public boolean sendFeedbackNotification(
            String type,
            int rating,
            String title,
            String details,
            String userName,
            String userEmail,
            String pageUrl,
            String userAgent,
            LocalDateTime createdAt) {

        String to = isBlank(feedbackToEmail) ? contactReceiverEmail : feedbackToEmail.trim();
        if (isBlank(to)) {
            System.err.println("Feedback email skipped: no FEEDBACK_TO_EMAIL / CONTACT_RECEIVER_EMAIL configured");
            return false;
        }

        String from = isBlank(feedbackFromEmail) ? fromEmail : feedbackFromEmail.trim();

        // Subject is built server-side and stripped of CR/LF to prevent mail
        // header injection via a crafted title.
        String safeTitle = title == null ? "" : title.replaceAll("[\\r\\n]+", " ").trim();
        if (safeTitle.length() > 90) safeTitle = safeTitle.substring(0, 90);
        String subject = switch (type) {
            case "bug"     -> "[Bug Report] " + safeTitle;
            case "feature" -> "[Feature Request] " + safeTitle;
            default        -> "[General Feedback] " + safeTitle;
        };

        String html = buildFeedbackNotificationHtml(
                type, rating, title, details, userName, userEmail, pageUrl, userAgent, createdAt);

        return sendEmail(fromName, from, to, subject, html);
    }

    private boolean sendEmail(String toEmail, String subject, String html) {
        return sendEmail(fromName, fromEmail, toEmail, subject, html);
    }

    private boolean sendEmail(String fromNameValue, String fromEmailValue,
                              String toEmail, String subject, String html) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey);

            Map<String, Object> emailData = new HashMap<>();
            emailData.put("from", fromNameValue + " <" + fromEmailValue + ">");
            emailData.put("to", new String[]{toEmail});
            emailData.put("subject", subject);
            emailData.put("html", html);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(emailData, headers);
            restTemplate.exchange(RESEND_API_URL, HttpMethod.POST, request, String.class);
            return true;
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
            return false;
        }
    }

    private String buildVerificationEmailHtml(String otp) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Email Verification</title>
            </head>
            <body style="margin: 0; padding: 0; font-family: Arial, sans-serif; background-color: #f4f4f4;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="background-color: #f4f4f4; padding: 20px;">
                    <tr>
                        <td align="center">
                            <table width="600" cellpadding="0" cellspacing="0" style="background-color: #ffffff; border-radius: 8px; overflow: hidden;">
                                <tr>
                                    <td style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 40px; text-align: center;">
                                        <h1 style="color: #ffffff; margin: 0; font-size: 28px;">ResumeForge AI</h1>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 40px;">
                                        <h2 style="color: #333333; margin-top: 0;">Verify Your Email Address</h2>
                                        <p style="color: #666666; line-height: 1.6;">Thank you for signing up! Please use the OTP below to verify your email address:</p>
                                        <div style="background-color: #f8f8f8; border: 2px dashed #667eea; border-radius: 8px; padding: 20px; text-align: center; margin: 30px 0;">
                                            <span style="font-size: 32px; font-weight: bold; color: #667eea; letter-spacing: 8px;">%s</span>
                                        </div>
                                        <p style="color: #666666; line-height: 1.6;">This OTP will expire in 10 minutes.</p>
                                        <p style="color: #666666; line-height: 1.6;">If you didn't create an account with ResumeForge AI, please ignore this email.</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color: #f8f8f8; padding: 20px; text-align: center;">
                                        <p style="color: #999999; font-size: 12px; margin: 0;">© 2024 ResumeForge AI. All rights reserved.</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(otp);
    }

    private String buildPasswordResetEmailHtml(String resetLink) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Password Reset</title>
            </head>
            <body style="margin: 0; padding: 0; font-family: Arial, sans-serif; background-color: #f4f4f4;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="background-color: #f4f4f4; padding: 20px;">
                    <tr>
                        <td align="center">
                            <table width="600" cellpadding="0" cellspacing="0" style="background-color: #ffffff; border-radius: 8px; overflow: hidden;">
                                <tr>
                                    <td style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 40px; text-align: center;">
                                        <h1 style="color: #ffffff; margin: 0; font-size: 28px;">ResumeForge AI</h1>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 40px;">
                                        <h2 style="color: #333333; margin-top: 0;">Reset Your Password</h2>
                                        <p style="color: #666666; line-height: 1.6;">We received a request to reset your password. Click the button below to proceed:</p>
                                        <div style="text-align: center; margin: 30px 0;">
                                            <a href="%s" style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); color: #ffffff; padding: 12px 30px; text-decoration: none; border-radius: 6px; display: inline-block; font-weight: bold;">Reset Password</a>
                                        </div>
                                        <p style="color: #666666; line-height: 1.6;">This link will expire in 1 hour.</p>
                                        <p style="color: #666666; line-height: 1.6;">If you didn't request a password reset, please ignore this email.</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color: #f8f8f8; padding: 20px; text-align: center;">
                                        <p style="color: #999999; font-size: 12px; margin: 0;">© 2024 ResumeForge AI. All rights reserved.</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(resetLink);
    }

    private String buildInvoiceEmailHtml(Payment payment) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
        String formattedDate = payment.getCreatedAt().format(formatter);
        
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Payment Invoice</title>
            </head>
            <body style="margin: 0; padding: 0; font-family: Arial, sans-serif; background-color: #f4f4f4;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="background-color: #f4f4f4; padding: 20px;">
                    <tr>
                        <td align="center">
                            <table width="600" cellpadding="0" cellspacing="0" style="background-color: #ffffff; border-radius: 8px; overflow: hidden;">
                                <tr>
                                    <td style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 40px; text-align: center;">
                                        <h1 style="color: #ffffff; margin: 0; font-size: 28px;">Payment Invoice</h1>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 40px;">
                                        <h2 style="color: #333333; margin-top: 0;">Thank You for Your Payment!</h2>
                                        <p style="color: #666666; line-height: 1.6;">Your premium subscription has been activated.</p>
                                        
                                        <table width="100%%" cellpadding="10" cellspacing="0" style="margin: 30px 0; border: 1px solid #e0e0e0; border-radius: 6px;">
                                            <tr>
                                                <td style="background-color: #f8f8f8; font-weight: bold; color: #333333;">Payment ID:</td>
                                                <td style="color: #666666;">%s</td>
                                            </tr>
                                            <tr>
                                                <td style="background-color: #f8f8f8; font-weight: bold; color: #333333;">Amount:</td>
                                                <td style="color: #666666;">₹%s</td>
                                            </tr>
                                            <tr>
                                                <td style="background-color: #f8f8f8; font-weight: bold; color: #333333;">Status:</td>
                                                <td style="color: #4caf50; font-weight: bold;">%s</td>
                                            </tr>
                                            <tr>
                                                <td style="background-color: #f8f8f8; font-weight: bold; color: #333333;">Date:</td>
                                                <td style="color: #666666;">%s</td>
                                            </tr>
                                        </table>
                                        
                                        <p style="color: #666666; line-height: 1.6;">If you have any questions, feel free to contact our support team.</p>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color: #f8f8f8; padding: 20px; text-align: center;">
                                        <p style="color: #999999; font-size: 12px; margin: 0;">© 2024 ResumeForge AI. All rights reserved.</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(
                payment.getRazorpayPaymentId(),
                payment.getAmount(),
                payment.getStatus(),
                formattedDate
            );
    }

    private String buildFeedbackNotificationHtml(
            String type, int rating, String title, String details,
            String userName, String userEmail, String pageUrl,
            String userAgent, LocalDateTime createdAt) {

        // Escape every user-supplied value so a crafted feedback payload cannot
        // inject markup/scripts into the operator notification email.
        String safeType    = escapeHtml(typeLabel(type));
        String safeTitle   = escapeHtml(title);
        String safeDetails = escapeHtml(details).replace("\n", "<br>");
        String safeName    = escapeHtml(isBlank(userName) ? "Anonymous" : userName);
        String safeEmail   = escapeHtml(isBlank(userEmail) ? "-" : userEmail);
        String safeUrl     = escapeHtml(isBlank(pageUrl) ? "-" : pageUrl);
        String safeUa      = escapeHtml(isBlank(userAgent) ? "-" : userAgent);
        String stars       = "\u2605".repeat(rating) + "\u2606".repeat(5 - rating) + " (" + rating + "/5)";
        String when        = createdAt == null
                ? "-"
                : createdAt.format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"));

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>New Feedback</title>
            </head>
            <body style="margin: 0; padding: 0; font-family: Arial, sans-serif; background-color: #f4f4f4;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="background-color: #f4f4f4; padding: 20px;">
                    <tr>
                        <td align="center">
                            <table width="600" cellpadding="0" cellspacing="0" style="background-color: #ffffff; border-radius: 8px; overflow: hidden;">
                                <tr>
                                    <td style="background: linear-gradient(135deg, #667eea 0%%, #764ba2 100%%); padding: 32px; text-align: center;">
                                        <h1 style="color: #ffffff; margin: 0; font-size: 24px;">New %s</h1>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding: 32px;">
                                        <h2 style="color: #333333; margin-top: 0;">%s</h2>
                                        <p style="color: #f5a623; font-size: 20px; margin: 8px 0;">%s</p>
                                        <table width="100%%" cellpadding="10" cellspacing="0" style="margin: 16px 0; border: 1px solid #e0e0e0; border-radius: 6px;">
                                            <tr><td style="background-color: #f8f8f8; font-weight: bold; color: #333333; width: 140px;">From</td><td style="color: #666666;">%s</td></tr>
                                            <tr><td style="background-color: #f8f8f8; font-weight: bold; color: #333333;">Email</td><td style="color: #666666;">%s</td></tr>
                                            <tr><td style="background-color: #f8f8f8; font-weight: bold; color: #333333;">Page</td><td style="color: #666666; word-break: break-all;">%s</td></tr>
                                            <tr><td style="background-color: #f8f8f8; font-weight: bold; color: #333333;">Device</td><td style="color: #666666; word-break: break-all;">%s</td></tr>
                                            <tr><td style="background-color: #f8f8f8; font-weight: bold; color: #333333;">Submitted</td><td style="color: #666666;">%s</td></tr>
                                        </table>
                                        <h3 style="color: #333333;">Details</h3>
                                        <div style="background-color: #f8f8f8; border-radius: 6px; padding: 16px; color: #444444; line-height: 1.6;">%s</div>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="background-color: #f8f8f8; padding: 20px; text-align: center;">
                                        <p style="color: #999999; font-size: 12px; margin: 0;">Sent automatically by the ResumeForge AI feedback system.</p>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(
                safeType,
                safeTitle,
                stars,
                safeName,
                safeEmail,
                safeUrl,
                safeUa,
                when,
                safeDetails
            );
    }

    private String typeLabel(String type) {
        return switch (type == null ? "" : type) {
            case "bug"     -> "Bug Report";
            case "feature" -> "Feature Request";
            default        -> "General Feedback";
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String buildContactNotificationHtml(String name, String email, String message) {
        // SEC FIX: escape HTML metacharacters so a crafted contact message
        // cannot inject markup/scripts into the admin notification email.
        String safeName = escapeHtml(name);
        String safeEmail = escapeHtml(email);
        String safeMessage = escapeHtml(message);
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>New Contact Form Submission</title>
            </head>
            <body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333333;">
                <h2>New Contact Form Submission</h2>
                <p><strong>Name:</strong> %s</p>
                <p><strong>Email:</strong> %s</p>
                <p><strong>Message:</strong></p>
                <p>%s</p>
            </body>
            </html>
            """.formatted(safeName, safeEmail, safeMessage);
    }

    private String escapeHtml(String value) {
        if (value == null) return "";
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
