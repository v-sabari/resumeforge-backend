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
public class SnapshotResponse {
    private Long id;
    private Long resumeId;
    private String snapshotData;
    private LocalDateTime createdAt;
}
