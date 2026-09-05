package com.resumeforge.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// GOOGLE SIGN-IN: request body for POST /api/auth/google. The client sends the
// raw credential (JWT) string returned by the Google Identity Services script.
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoogleAuthRequest {
    @NotBlank(message = "Credential is required")
    private String credential;

    // Optional referral code captured from the register page's ?ref= param so
    // Google signups are tracked/rewarded exactly like email signups.
    private String referralCode;
}