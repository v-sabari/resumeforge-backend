package com.resumeforge.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AiRequest {
    @NotBlank(message = "Content is required")
    private String content;
    
    private String context;
    private String jobDescription;
}
