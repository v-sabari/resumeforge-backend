package com.cvcraft.ai.dto;
public record ExportRecordResponse(boolean recorded, int totalExports, int remainingFreeExports, String message) {}
