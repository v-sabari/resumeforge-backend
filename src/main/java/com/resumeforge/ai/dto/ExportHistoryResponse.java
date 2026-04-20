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
public class ExportHistoryResponse {
    private Long id;
    private Long resumeId;
    private String exportFormat;
    private LocalDateTime createdAt;
}
