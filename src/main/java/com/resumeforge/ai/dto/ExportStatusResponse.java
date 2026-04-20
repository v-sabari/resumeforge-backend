package com.resumeforge.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportStatusResponse {
    private boolean canExport;
    private String reason;
    private long exportsToday;
    private long exportLimit;
}
