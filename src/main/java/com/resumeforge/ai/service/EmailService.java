package com.resumeforge.ai.service;

import com.resumeforge.ai.entity.Payment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
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

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    public void sendVerificationEmail(String toEmail, String otp) {
        String subject = "Verify Your Email - ResumeForge AI";
        String html = buildVerificationEmailHtml(otp);
        sendEmail(toEmail, subject, html);
    }

    public void sendPasswordResetEmail(String toEmail, String resetToken) {
        String subject = "Reset Your Password - ResumeForge AI";
        String resetLink = "https://www.resumeforgeai.site/reset-password?token=" + resetToken;
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

    private void sendEmail(String toEmail, String subject, String html) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(resendApiKey);

            Map<String, Object> emailData = new HashMap<>();
            emailData.put("from", fromName + " <" + fromEmail + ">");
            emailData.put("to", new String[]{toEmail});
            emailData.put("subject", subject);
            emailData.put("html", html);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(emailData, headers);
            restTemplate.exchange(RESEND_API_URL, HttpMethod.POST, request, String.class);
        } catch (Exception e) {
            System.err.println("Failed to send email: " + e.getMessage());
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

    private String buildContactNotificationHtml(String name, String email, String message) {
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
            """.formatted(name, email, message);
    }
}
