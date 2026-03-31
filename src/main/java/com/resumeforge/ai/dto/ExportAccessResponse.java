package com.resumeforge.ai.dto;

public record ExportAccessResponse(
        boolean allowed,
        boolean premium,
        boolean adRequired,
        boolean adCompleted,
        int usedExports,
        int remainingFreeExports,
        String reason,
        String message
) {
}
