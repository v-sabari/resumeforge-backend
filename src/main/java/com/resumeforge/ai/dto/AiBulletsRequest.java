package com.resumeforge.ai.dto;

import java.util.List;

public record AiBulletsRequest(
        String role,
        String company,
        List<String> responsibilities,
        List<String> technologies,
        String currentText
) {
}
