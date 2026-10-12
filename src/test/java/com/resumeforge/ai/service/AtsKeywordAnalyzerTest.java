package com.resumeforge.ai.service;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TOOL-01: deterministic unit tests for the keyword analyzer. No Spring
 * context, no AI — same input always yields the same output.
 */
class AtsKeywordAnalyzerTest {

    private final AtsKeywordAnalyzer analyzer = new AtsKeywordAnalyzer();

    private AtsKeywordAnalyzer.Term find(AtsKeywordAnalyzer.Analysis a, String keyword) {
        String key = AtsKeywordAnalyzer.normalize(keyword);
        for (AtsKeywordAnalyzer.Term t : a.all) {
            if (AtsKeywordAnalyzer.normalize(t.keyword).equals(key)) return t;
        }
        return null;
    }

    @Test
    void matchesRequiredAndPreferredTermsAndFlagsMissing() {
        String resume = "Skilled engineer. Skills: Java, Spring Framework, Docker. Experienced with AWS.";
        String jd = "Backend Engineer\n"
                + "Required qualifications: Java, Spring Boot, Docker, Kubernetes.\n"
                + "Preferred: AWS, Terraform.";

        AtsKeywordAnalyzer.Analysis a = analyzer.analyze(resume, jd);

        AtsKeywordAnalyzer.Term java = find(a, "java");
        assertThat(java).isNotNull();
        assertThat(java.matchType).isEqualTo("EXACT");
        assertThat(java.required).isTrue();

        // "Spring Boot" in the JD canonicalises to "spring" and is satisfied by
        // "Spring Framework" already present in the resume.
        AtsKeywordAnalyzer.Term spring = find(a, "spring");
        assertThat(spring).isNotNull();
        assertThat(spring.matchType).isEqualTo("EXACT");

        AtsKeywordAnalyzer.Term docker = find(a, "docker");
        assertThat(docker).isNotNull();
        assertThat(docker.matchType).isEqualTo("EXACT");

        AtsKeywordAnalyzer.Term kubernetes = find(a, "kubernetes");
        assertThat(kubernetes).isNotNull();
        assertThat(kubernetes.matchType).isEqualTo("MISSING");
        assertThat(kubernetes.required).isTrue();

        AtsKeywordAnalyzer.Term aws = find(a, "aws");
        assertThat(aws).isNotNull();
        assertThat(aws.matchType).isEqualTo("EXACT");
        assertThat(aws.required).isFalse(); // listed under "Preferred:"

        assertThat(a.requiredTotal).isGreaterThanOrEqualTo(3);
        assertThat(a.missing).extracting(t -> t.keyword).contains("kubernetes");
    }

    @Test
    void aliasMatchingIsWholeTokenAndCaseInsensitive() {
        String resume = "Proficient in JS and Node.";
        String jd = "Required: JavaScript, Node.";

        AtsKeywordAnalyzer.Analysis a = analyzer.analyze(resume, jd);

        AtsKeywordAnalyzer.Term js = find(a, "javascript");
        assertThat(js).isNotNull();
        assertThat(js.matchType).isEqualTo("EXACT"); // satisfied by the "JS" alias
    }

    @Test
    void doesNotFlagEquivalentTermAsMissing() {
        String resume = "Built services with PostgreSQL.";
        String jd = "Required: Postgres.";

        AtsKeywordAnalyzer.Analysis a = analyzer.analyze(resume, jd);

        AtsKeywordAnalyzer.Term pg = find(a, "postgresql");
        assertThat(pg).isNotNull();
        assertThat(pg.matchType).isEqualTo("EXACT"); // "Postgres" alias
    }

    @Test
    void applySemanticMatchesMovesMissingToSemanticAndRecomputesCoverage() {
        String resume = "Backend developer using containerization.";
        String jd = "Required: Docker.";

        AtsKeywordAnalyzer.Analysis a = analyzer.analyze(resume, jd);
        AtsKeywordAnalyzer.Term docker = find(a, "docker");
        assertThat(docker).isNotNull();
        assertThat(docker.matchType).isEqualTo("MISSING");
        int beforeRequired = a.requiredMatched;

        Map<String, String> semantic = new LinkedHashMap<>();
        semantic.put("docker", "containerization");
        analyzer.applySemanticMatches(a, semantic);

        assertThat(docker.matchType).isEqualTo("SEMANTIC");
        assertThat(docker.matchedWith).isEqualTo("containerization");
        assertThat(a.missing).extracting(t -> t.keyword).doesNotContain("docker");
        assertThat(a.matched).extracting(t -> t.keyword).contains("docker");
        assertThat(a.requiredMatched).isEqualTo(beforeRequired + 1);
    }

    @Test
    void unknownSemanticKeywordIsIgnored() {
        String resume = "Java developer.";
        String jd = "Required: Java.";

        AtsKeywordAnalyzer.Analysis a = analyzer.analyze(resume, jd);
        Map<String, String> semantic = new LinkedHashMap<>();
        semantic.put("quantum-computing", "something");
        analyzer.applySemanticMatches(a, semantic);

        // The AI must not be able to introduce a term that was not in the JD.
        assertThat(a.all).extracting(t -> t.keyword).doesNotContain("quantum-computing");
    }
}
