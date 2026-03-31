package com.resumeforge.ai.dto;

public record ExportRecordResponse(
        boolean recorded,
        int usedExports,
        int remainingFreeExports,
        String message
) {
}
