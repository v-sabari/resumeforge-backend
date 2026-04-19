package com.resumeforge.ai.dto;

import java.util.List;

/**
 * Input for grammar and clarity checking.
 * Free for all users, no daily cap (covered by rate limiter).
 */
public record GrammarCheckRequest(
        /** The text to check. Max 3000 characters enforced in service. */
        String text,
        /** Context hint: "summary" | "bullet" | "cover_letter" | "general" */
        String context
) {}
