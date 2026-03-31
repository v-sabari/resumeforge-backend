package com.resumeforge.ai.dto;

public record AiRewriteRequest(
        String text,
        String tone,
        String targetRole
) {
}
