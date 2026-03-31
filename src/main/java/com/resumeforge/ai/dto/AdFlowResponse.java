package com.resumeforge.ai.dto;

public record AdFlowResponse(
        String status,
        boolean adCompleted,
        String message
) {
}
