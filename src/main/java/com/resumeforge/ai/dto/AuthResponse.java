package com.resumeforge.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private UserResponse user;
    // GOOGLE SIGN-IN: true when this login created a brand-new account,
    // false when an existing account was signed in. Lets the frontend
    // branch on first-time signup vs returning-user login.
    private boolean isNewUser;
}
