package com.resumeforge.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    // BUILDER-06 FIX: Frontend history drawer reads item.snapshotId, not item.id.
    // Adding this alias ensures both field names work without breaking any consumer.
    private Long snapshotId;

    private Long resumeId;

    // BUILDER-06 FIX: snapshotData is a raw JSON dump of the full resume — it should
    // never be sent to the browser (leaks internal state, wastes bandwidth).
    // It is only used server-side during restore. Annotate with @JsonIgnore.
    @JsonIgnore
    private String snapshotData;

    // BUILDER-06 FIX: Frontend expects item.label for display. Backend never set it,
    // so the history drawer showed "undefined" as the version title.
    // Provide a human-readable label based on the creation timestamp.
    private String label;

    private LocalDateTime createdAt;
}