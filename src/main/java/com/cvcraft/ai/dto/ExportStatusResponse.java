package com.cvcraft.ai.dto;
public record ExportStatusResponse(boolean premium, int usedExports, int remainingFreeExports,
    boolean adCompleted, boolean canExport, String message) {}
