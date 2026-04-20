package com.resumeforge.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SetRoleRequest {
    @NotBlank(message = "Role is required")
    private String role;
}
