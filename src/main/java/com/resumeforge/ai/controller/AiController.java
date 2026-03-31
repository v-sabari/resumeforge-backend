package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.*;
import com.resumeforge.ai.service.AiService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiController {
    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @PostMapping("/summary")
    public AiTextResponse summary(@RequestBody AiSummaryRequest request) {
        return aiService.generateSummary(request);
    }

    @PostMapping("/bullets")
    public AiListResponse bullets(@RequestBody AiBulletsRequest request) {
        return aiService.generateBullets(request);
    }

    @PostMapping("/skills")
    public AiListResponse skills(@RequestBody AiSkillsRequest request) {
        return aiService.suggestSkills(request);
    }

    @PostMapping("/rewrite")
    public AiTextResponse rewrite(@RequestBody AiRewriteRequest request) {
        return aiService.rewrite(request);
    }
}
