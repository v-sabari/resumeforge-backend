package com.resumeforge.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String name;
    private String email;
    private String role;
    private boolean premium;
    private boolean emailVerified;
    private String referralCode;
    // UX-05 FIX: createdAt was missing from UserResponse. User entity has
    // this field as Instant. ProfilePage reads user?.createdAt for "Member since"
    // display — it always showed "—" because the field was never included in the response.
    private Instant createdAt;
}