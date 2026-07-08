package com.resumeforge.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ExportRecordRequest {
    @NotNull(message = "Resume ID is required")
    private Long resumeId;

    @NotBlank(message = "Format is required")
    private String format;
}
