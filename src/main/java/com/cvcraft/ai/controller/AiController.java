package com.cvcraft.ai.controller;
import com.cvcraft.ai.dto.*;
import com.cvcraft.ai.service.AiService;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/ai")
public class AiController {
    private final AiService aiService;
    public AiController(AiService a) { this.aiService = a; }
    @PostMapping("/summary") public AiTextResponse summary(@RequestBody AiSummaryRequest req) { return aiService.generateSummary(req); }
    @PostMapping("/bullets") public AiListResponse bullets(@RequestBody AiBulletsRequest req) { return aiService.generateBullets(req); }
    @PostMapping("/skills")  public AiListResponse skills(@RequestBody AiSkillsRequest req)   { return aiService.suggestSkills(req); }
    @PostMapping("/rewrite") public AiTextResponse rewrite(@RequestBody AiRewriteRequest req) { return aiService.rewrite(req); }
}
