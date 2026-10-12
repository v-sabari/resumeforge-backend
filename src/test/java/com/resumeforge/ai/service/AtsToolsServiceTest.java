package com.resumeforge.ai.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.resumeforge.ai.dto.AtsScoreResponse;
import com.resumeforge.ai.dto.FormatCheckResponse;
import com.resumeforge.ai.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TOOL-01 / TOOL-02: end-to-end service tests using the real deterministic
 * pipeline (real parser, analyzer and format checker) with a mocked AI client,
 * so they run without OPENROUTER_API_KEY and without a Spring context.
 */
class AtsToolsServiceTest {

    private final DocumentParsingService parsing = new DocumentParsingService();
    private final AtsKeywordAnalyzer analyzer = new AtsKeywordAnalyzer();
    private final FormatChecker checker = new FormatChecker();
    private final AiService aiService = Mockito.mock(AiService.class);

    private AtsToolsService service;

    @BeforeEach
    void setUp() {
        service = new AtsToolsService(parsing, analyzer, checker, aiService);
    }

    private byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    void atsScoreIsFullyDeterministicWhenAiIsUnavailable() {
        Mockito.when(aiService.publicSemanticMatch(Mockito.anyString())).thenReturn(null);

        String resume = "John Doe\njohn@example.com\nSkills\nJava, Docker, SQL\n"
                + "Experience\nEngineer 2018-2023\nEducation\nB.Tech";
        String jd = "We need a backend engineer. Required: Java, Docker, Kubernetes, SQL.";

        AtsScoreResponse r = service.atsScore(bytes(resume), "resume.txt", "text/plain", jd);

        assertThat(r.getScore()).isBetween(0, 100);
        assertThat(r.isAiEnhanced()).isFalse();
        assertThat(r.getMatchedKeywords()).contains("java", "docker", "sql");
        assertThat(r.getMissingKeywords()).extracting(AtsScoreResponse.KeywordItem::getKeyword)
                .contains("kubernetes");
        assertThat(r.getFactorScores()).hasSize(6);
        assertThat(r.getJobWordCount()).isGreaterThan(0);
        assertThat(r.getGrade()).isNotBlank();
    }

    @Test
    void atsScoreAppliesSemanticMatchesReturnedByAi() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode response = mapper.readTree(
                "{\"matches\":[{\"missing\":\"kubernetes\",\"resumeTerm\":\"container orchestration\"}]}");
        Mockito.when(aiService.publicSemanticMatch(Mockito.anyString())).thenReturn(response);

        String resume = "Backend engineer with container orchestration experience. Java and SQL.";
        String jd = "Required: Java, SQL, Kubernetes. This role builds distributed systems at scale.";

        AtsScoreResponse r = service.atsScore(bytes(resume), "resume.txt", "text/plain", jd);

        assertThat(r.isAiEnhanced()).isTrue();
        assertThat(r.getSemanticMatches()).anyMatch(s -> s.contains("kubernetes"));
        assertThat(r.getMissingKeywords()).extracting(AtsScoreResponse.KeywordItem::getKeyword)
                .doesNotContain("kubernetes");
    }

    @Test
    void aiFailureNeverBreaksTheDeterministicResult() {
        Mockito.when(aiService.publicSemanticMatch(Mockito.anyString()))
                .thenThrow(new RuntimeException("simulated AI outage"));

        String resume = "Java developer with SQL skills.";
        String jd = "Required: Java, SQL, Kubernetes, Docker. Build reliable backend systems.";

        AtsScoreResponse r = service.atsScore(bytes(resume), "resume.txt", "text/plain", jd);

        assertThat(r.isAiEnhanced()).isFalse();
        assertThat(r.getScore()).isBetween(0, 100);
    }

    @Test
    void shortJobDescriptionIsRejected() {
        assertThatThrownBy(() -> service.atsScore(bytes("Some resume"), "r.txt", "text/plain", "too short"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void unsupportedFormatReturnsAClearMessage() {
        assertThatThrownBy(() -> service.formatCheck(new byte[]{1, 2, 3}, "resume.doc", "application/msword"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining(".doc");
    }

    @Test
    void emptyUploadIsRejected() {
        assertThatThrownBy(() -> service.formatCheck(new byte[0], "resume.txt", "text/plain"))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void formatCheckReturnsExtractedTextForTxt() {
        String resume = "John Doe\njohn@example.com\nSkills\nJava, Docker, SQL, AWS, Spring";
        FormatCheckResponse r = service.formatCheck(bytes(resume), "r.txt", "text/plain");
        assertThat(r.isTextExtracted()).isTrue();
        assertThat(r.getFileName()).isEqualTo("r.txt");
    }
}
