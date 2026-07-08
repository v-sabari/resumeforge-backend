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
    private String  reason;
    private long    exportsToday;
    private long    exportLimit;
    // ISSUE-02 FIX: Frontend reads v.remainingFreeExports ?? v.remaining ?? 0.
    // Without this field both fallbacks resolve to 0, showing "0 remaining" for every user.
    private long    remainingFreeExports;
}