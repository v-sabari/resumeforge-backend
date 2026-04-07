package com.resumeforge.ai.service;

import com.resumeforge.ai.exception.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

@Service
public class EmailService {

    @Value("${app.resend.api-key}")
    private String resendApiKey;

    @Value("${app.mail.from-email}")
    private String fromEmail;

    @Value("${app.mail.from-name}")
    private String fromName;

    public void sendVerificationOtp(String toEmail, String otpCode) {
        String subject = "Verify your ResumeForge AI account";
        String html = """
                <div style="font-family: Arial, sans-serif; line-height: 1.6; color: #111827;">
                  <h2 style="margin-bottom: 8px;">Verify your email</h2>
                  <p>Thanks for signing up for <strong>ResumeForge AI</strong>.</p>
                  <p>Use the OTP below to verify your email address:</p>
                  <div style="font-size: 28px; font-weight: 700; letter-spacing: 6px; margin: 20px 0; color: #2563eb;">
                    %s
                  </div>
                  <p>This OTP expires in <strong>5 minutes</strong>.</p>
                  <p>If you did not create this account, you can safely ignore this email.</p>
                </div>
                """.formatted(otpCode);

        sendEmail(toEmail, subject, html);
    }

    private void sendEmail(String toEmail, String subject, String html) {
        try {
            URL url = new URL("https://api.resend.com/emails");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + resendApiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            String json = """
                    {
                      "from": "%s <%s>",
                      "to": ["%s"],
                      "subject": "%s",
                      "html": %s
                    }
                    """.formatted(
                    escapeJson(fromName),
                    escapeJson(fromEmail),
                    escapeJson(toEmail),
                    escapeJson(subject),
                    toJsonString(html)
            );

            try (OutputStream os = connection.getOutputStream()) {
                os.write(json.getBytes(StandardCharsets.UTF_8));
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not send verification email");
            }

        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Could not send verification email");
        }
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String toJsonString(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "") + "\"";
    }
}