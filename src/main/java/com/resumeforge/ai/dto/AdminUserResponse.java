package com.resumeforge.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {
    private Long id;
    private String name;
    private String email;
    private String role;
    private boolean premium;
    private boolean emailVerified;
    private String referralCode;
    private Long referredByUserId;
    private LocalDateTime createdAt;
}
