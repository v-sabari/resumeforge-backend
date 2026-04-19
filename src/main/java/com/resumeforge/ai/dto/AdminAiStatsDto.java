package com.resumeforge.ai.dto;

import java.util.List;

// ── AI feature usage stats ─────────────────────────────────────────────────────
public record AdminAiStatsDto(
        long totalCallsLast30Days,
        long totalTokensLast30Days,
        List<FeatureCount> featureBreakdown
) {
    public record FeatureCount(String feature, long count) {}
}

