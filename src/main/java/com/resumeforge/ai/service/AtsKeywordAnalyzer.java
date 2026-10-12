package com.resumeforge.ai.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TOOL-01: deterministic keyword extraction, matching and coverage scoring for
 * the JD-Based ATS Score Checker.
 *
 * <p>This class contains NO AI and NO randomness — the same resume + JD always
 * produce the same result, and every number is derived from the actual input
 * text. The optional AI pass ({@link AtsToolsService}) only ever refines which
 * candidate terms count as semantically covered and adds natural-language
 * suggestions; it can never invent a score, because the score is computed here
 * from the matched-term set.</p>
 *
 * <p>Matching strategy, in order: exact phrase → normalised phrase → known
 * synonym/alias. A term is only reported as MISSING when none of those forms
 * appear in the resume, so an equivalent term present in the resume is never
 * incorrectly flagged as missing.</p>
 */
@Service
public class AtsKeywordAnalyzer {

    // ── Skill lexicon (canonical phrase -> aliases) ──────────────────────────
    // Kept deliberately broad but curated: multi-word phrases are preferred, and
    // every entry maps every known spelling to one canonical keyword.
    private static final Map<String, String[]> SKILLS = new LinkedHashMap<>();
    static {
        // Languages & core
        add("javascript", "js", "es6", "ecmascript");
        add("typescript", "ts");
        add("python", "py");
        add("java", "core java");
        add("c++", "cpp");
        add("c#", "csharp", "dotnet");
        add("golang", "go");
        add("rust");
        add("kotlin");
        add("swift");
        add("php");
        add("ruby");
        add("scala");
        add("matlab");
        add("sql");
        add("mysql");
        add("postgresql", "postgres");
        add("mongodb", "mongo");
        add("oracle");
        add("sql server", "mssql", "sqlserver");
        add("redis");
        add("elasticsearch", "elastic search");
        add("cassandra");
        add("dynamodb");
        // Frontend
        add("react", "react js", "reactjs");
        add("angular", "angularjs");
        add("vue", "vue js", "vuejs");
        add("next js", "nextjs");
        add("html", "html5");
        add("css", "css3");
        add("sass", "scss");
        add("tailwind", "tailwind css");
        add("bootstrap");
        // Backend / API
        add("node js", "nodejs", "node");
        add("express", "express js", "expressjs");
        add("django");
        add("flask");
        add("spring", "spring boot", "springboot");
        add("laravel");
        add("rails", "ruby on rails");
        add(".net core");
        add("rest api", "restful api", "restful");
        add("graphql");
        add("grpc");
        add("microservices", "micro services", "microservice");
        add("api", "apis", "api design");
        // DevOps / cloud
        add("docker");
        add("docker compose");
        add("kubernetes", "k8s");
        add("terraform");
        add("ansible");
        add("jenkins");
        add("github actions");
        add("gitlab", "gitlab ci");
        add("ci cd", "ci/cd", "ci cd pipeline", "continuous integration", "continuous delivery");
        add("aws", "amazon web services");
        add("azure", "microsoft azure");
        add("gcp", "google cloud", "google cloud platform");
        add("aws lambda", "lambda");
        add("s3", "amazon s3");
        add("ec2");
        add("linux", "unix");
        add("git", "github", "bitbucket");
        add("jira");
        add("confluence");
        add("agile");
        add("scrum");
        add("kanban");
        add("devops");
        // Data / ML
        add("machine learning", "ml");
        add("deep learning", "dl");
        add("artificial intelligence", "ai");
        add("data science");
        add("data analysis", "data analytics");
        add("data engineering");
        add("nlp", "natural language processing");
        add("computer vision");
        add("tensorflow", "tensor flow");
        add("pytorch", "torch");
        add("scikit learn", "sklearn", "scikit");
        add("pandas");
        add("numpy");
        add("keras");
        add("opencv");
        add("hadoop");
        add("spark", "apache spark", "pyspark");
        add("kafka", "apache kafka");
        add("airflow", "apache airflow");
        add("etl", "etl pipeline", "etl pipelines");
        add("tableau");
        add("power bi", "powerbi");
        add("looker");
        add("excel", "microsoft excel", "advanced excel");
        // Business / soft skills
        add("communication");
        add("collaboration");
        add("teamwork", "team work");
        add("problem solving");
        add("critical thinking");
        add("adaptability");
        add("time management");
        add("attention to detail");
        add("leadership");
        add("presentation skills");
        add("customer service");
        add("sales");
        add("marketing");
        add("seo", "search engine optimization");
        add("content writing");
        add("copywriting", "copy writing");
        add("accounting");
        add("finance");
        add("financial analysis");
    }

    private static void add(String canonical, String... aliases) {
        SKILLS.put(canonical, aliases.length == 0 ? new String[0] : aliases);
    }

    /** phrase (canonical or alias) -> canonical, for JD extraction. */
    private static final Map<String, String> LOOKUP = new LinkedHashMap<>();
    static {
        for (Map.Entry<String, String[]> e : SKILLS.entrySet()) {
            LOOKUP.put(normalize(e.getKey()), e.getKey());
            for (String a : e.getValue()) {
                LOOKUP.putIfAbsent(normalize(a), e.getKey());
            }
        }
    }

    private static final List<String> LOOKUP_PHRASES = LOOKUP.keySet().stream()
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();

    private static final Set<String> STOPWORDS = Set.of(
            "the", "and", "for", "with", "you", "your", "our", "are", "will", "have", "has",
            "this", "that", "from", "not", "but", "all", "any", "can", "who", "what", "when",
            "where", "how", "why", "its", "it's", "into", "out", "about", "over", "under",
            "their", "they", "them", "his", "her", "she", "him", "was", "were", "been",
            "being", "able", "must", "should", "would", "could", "may", "might", "each",
            "other", "more", "most", "some", "such", "than", "then", "there", "these",
            "those", "through", "very", "well", "also", "across", "using", "use", "used",
            "work", "working", "role", "job", "team", "teams", "company", "candidate",
            "years", "year", "experience", "experiences", "required", "requirement",
            "requirements", "preferred", "responsibilities", "responsibility", "skills",
            "skill", "ability", "knowledge", "strong", "good", "excellent", "plus",
            "including", "include", "includes", "etc", "e.g", "i.e", "new"
    );

    private static final Pattern REQUIRED_HDR = Pattern.compile(
            "(?i)\\b(required|requirements|must have|must-have|qualifications|minimum|basic qualifications|what you.ll need|what we.re looking for|you have)\\b");
    private static final Pattern PREFERRED_HDR = Pattern.compile(
            "(?i)\\b(preferred|nice to have|nice-to-have|good to have|bonus|desired|plus|advantage|preferred qualifications|added advantage)\\b");
    private static final Pattern YEAR_REQ = Pattern.compile(
            "(\\d{1,2})\\s*\\+?\\s*(?:years?|yrs?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEAR_TOKEN = Pattern.compile("(19|20)\\d{2}");
    private static final Set<String> EDU_KEYWORDS = Set.of(
            "bachelor", "bachelors", "master", "masters", "phd", "doctorate", "mba",
            "b.tech", "btech", "b.e", "be", "b.sc", "bsc", "m.sc", "msc", "m.tech", "mtech",
            "degree", "graduation", "graduate", "diploma", "university", "college", "bca", "mca");

    private static final int MAX_TERMS = 60;

    // ── Public API ────────────────────────────────────────────────────────────

    /** One job-description term and its match state against the resume. */
    public static class Term {
        public final String keyword;
        public boolean required;
        public int importance;
        public String matchType = "MISSING"; // EXACT | SEMANTIC | MISSING
        public String matchedWith;

        Term(String keyword, boolean required, int importance) {
            this.keyword = keyword;
            this.required = required;
            this.importance = importance;
        }
    }

    public static class Analysis {
        public final List<Term> all = new ArrayList<>();
        public final List<Term> matched = new ArrayList<>();
        public final List<Term> missing = new ArrayList<>();
        public int requiredCoverage;
        public int preferredCoverage;
        public int keywordCoverage;
        public int experienceAlignment;
        public int educationAlignment;
        public int requiredTotal;
        public int requiredMatched;
        public int preferredTotal;
        public int preferredMatched;
        public boolean jobHasExperienceRequirement;
        public boolean jobHasEducationRequirement;
    }

    public Analysis analyze(String resumeText, String jobDescription) {
        Analysis a = new Analysis();
        String normResume = normalize(resumeText == null ? "" : resumeText);
        String jd = jobDescription == null ? "" : jobDescription;

        Map<String, Term> terms = extractTerms(jd);
        for (Term t : terms.values()) {
            resolveMatch(t, normResume);
            a.all.add(t);
            if ("MISSING".equals(t.matchType)) {
                a.missing.add(t);
            } else {
                a.matched.add(t);
            }
        }

        a.missing.sort(Comparator.comparingInt((Term t) -> t.importance).reversed());
        a.matched.sort(Comparator.comparingInt((Term t) -> t.importance).reversed());

        for (Term t : a.all) {
            boolean hit = !"MISSING".equals(t.matchType);
            if (t.required) {
                a.requiredTotal++;
                if (hit) a.requiredMatched++;
            } else {
                a.preferredTotal++;
                if (hit) a.preferredMatched++;
            }
        }

        a.requiredCoverage = pct(a.requiredMatched, a.requiredTotal);
        a.preferredCoverage = a.preferredTotal == 0 ? 100 : pct(a.preferredMatched, a.preferredTotal);
        a.keywordCoverage = pct(a.matched.size(), a.all.size());
        a.experienceAlignment = experienceAlignment(jd, normalize(resumeText == null ? "" : resumeText), a);
        a.educationAlignment = educationAlignment(jd, normResume, a);
        return a;
    }

    /**
     * Applies AI-provided semantic equivalences: any MISSING term the AI
     * confirmed as covered moves to SEMANTIC and the coverage numbers are
     * recomputed. Unknown keywords are ignored so the AI cannot invent terms.
     */
    public void applySemanticMatches(Analysis a, Map<String, String> semanticMatches) {
        if (semanticMatches == null || semanticMatches.isEmpty()) return;

        Map<String, Term> byKey = new LinkedHashMap<>();
        for (Term t : a.all) byKey.put(normalize(t.keyword), t);

        for (Map.Entry<String, String> e : semanticMatches.entrySet()) {
            Term t = byKey.get(normalize(e.getKey()));
            if (t == null || !"MISSING".equals(t.matchType)) continue;
            t.matchType = "SEMANTIC";
            t.matchedWith = e.getValue();
            a.missing.remove(t);
            a.matched.add(t);
        }

        a.matched.sort(Comparator.comparingInt((Term x) -> x.importance).reversed());
        a.requiredMatched = 0;
        a.preferredMatched = 0;
        for (Term t : a.all) {
            boolean hit = !"MISSING".equals(t.matchType);
            if (t.required && hit) a.requiredMatched++;
            if (!t.required && hit) a.preferredMatched++;
        }
        a.requiredCoverage = pct(a.requiredMatched, a.requiredTotal);
        a.preferredCoverage = a.preferredTotal == 0 ? 100 : pct(a.preferredMatched, a.preferredTotal);
        a.keywordCoverage = pct(a.matched.size(), a.all.size());
    }

    // ── Term extraction ───────────────────────────────────────────────────────

    private Map<String, Term> extractTerms(String jd) {
        Map<String, Term> terms = new LinkedHashMap<>();
        String normJd = normalize(jd);

        // Build per-line section context (required vs preferred). Terms are
        // classified by the line(s) they actually appear on, so a keyword under
        // "Preferred:" is not counted as a must-have requirement.
        String[] rawLines = jd.split("\\r?\\n", -1);
        String[] normLines = new String[rawLines.length];
        boolean[] requiredByLine = new boolean[rawLines.length];
        int[] lineStart = new int[rawLines.length];
        boolean currentRequired = true;
        int offset = 0;
        for (int i = 0; i < rawLines.length; i++) {
            String line = rawLines[i] == null ? "" : rawLines[i];
            lineStart[i] = offset;
            offset += line.length() + 1;
            normLines[i] = normalize(line);

            String trimmed = line.trim();
            if (!trimmed.isEmpty() && trimmed.length() <= 80) {
                if (PREFERRED_HDR.matcher(trimmed).find()) currentRequired = false;
                else if (REQUIRED_HDR.matcher(trimmed).find()) currentRequired = true;
            }
            requiredByLine[i] = currentRequired;
        }
        int jdLen = Math.max(1, jd.length());

        // 1) Lexicon phrases (canonicalised), scanned line by line. Longest
        //    phrases first so "machine learning" wins over a bare "learning".
        for (int i = 0; i < normLines.length; i++) {
            String line = normLines[i];
            if (line.isEmpty()) continue;
            final boolean requiredHere = requiredByLine[i];
            final int lineOffset = lineStart[i];
            for (String phrase : LOOKUP_PHRASES) {
                if (phrase.length() < 2) continue;
                int idx = indexOfPhrase(line, phrase);
                if (idx < 0) continue;
                String canonical = LOOKUP.get(phrase);
                if (canonical == null) continue;
                Term t = terms.computeIfAbsent(canonical, k -> new Term(canonical, requiredHere, 0));
                t.importance += 15 + positionBoost(lineOffset + idx, normJd.length())
                        + frequencyBoost(countPhrase(line, phrase));
                if (requiredHere) t.required = true;
            }
        }

        // 2) Acronyms (all-caps, 2-6 chars) e.g. AWS, SQL, API, CI.
        Pattern acronym = Pattern.compile("\\b[A-Z][A-Z0-9+#]{1,5}\\b");
        for (int i = 0; i < rawLines.length; i++) {
            final boolean requiredHere = requiredByLine[i];
            final int lineOffset = lineStart[i];
            Matcher acr = acronym.matcher(rawLines[i] == null ? "" : rawLines[i]);
            while (acr.find()) {
                String key = normalize(acr.group());
                if (key.length() < 2 || STOPWORDS.contains(key)) continue;
                String canonical = LOOKUP.getOrDefault(key, acr.group().toUpperCase(Locale.ROOT));
                Term t = terms.computeIfAbsent(canonical, k -> new Term(canonical, requiredHere, 0));
                t.importance += 6 + positionBoost(lineOffset + acr.start(), Math.max(1, jd.length()));
                if (requiredHere) t.required = true;
            }
        }

        // 3) Frequent meaningful words (freq >= 2, len >= 4, non-stopword).
        Map<String, int[]> freq = new LinkedHashMap<>();
        for (String word : normJd.split(" ")) {
            if (word.length() < 4 || STOPWORDS.contains(word) || word.matches("\\d+")) continue;
            freq.computeIfAbsent(word, k -> new int[1])[0]++;
        }
        freq.entrySet().stream()
                .filter(e -> e.getValue()[0] >= 2)
                .sorted((x, y) -> Integer.compare(y.getValue()[0], x.getValue()[0]))
                .limit(15)
                .forEach(e -> {
                    String canonical = e.getKey();
                    if (terms.containsKey(canonical)) return;
                    Term t = terms.computeIfAbsent(canonical, k -> new Term(k, false, 0));
                    t.importance += 4 + frequencyBoost(e.getValue()[0]);
                });

        // Rank + cap.
        List<Term> ranked = new ArrayList<>(terms.values());
        ranked.sort(Comparator.comparingInt((Term t) -> t.importance).reversed());
        Map<String, Term> capped = new LinkedHashMap<>();
        for (Term t : ranked) {
            if (capped.size() >= MAX_TERMS) break;
            capped.put(t.keyword, t);
        }
        return capped;
    }

    private void resolveMatch(Term t, String normResume) {
        // Forms: canonical + aliases.
        List<String> forms = new ArrayList<>();
        forms.add(normalize(t.keyword));
        String[] aliases = SKILLS.get(t.keyword);
        if (aliases != null) {
            for (String a : aliases) forms.add(normalize(a));
        }
        for (String form : forms) {
            if (form.isEmpty()) continue;
            if (indexOfPhrase(normResume, form) >= 0) {
                t.matchType = "EXACT";
                t.matchedWith = t.keyword;
                return;
            }
        }
        t.matchType = "MISSING";
    }

    // ── Alignments ────────────────────────────────────────────────────────────

    private int experienceAlignment(String jd, String normResume, Analysis a) {
        Matcher req = YEAR_REQ.matcher(jd);
        int requiredYears = 0;
        while (req.find()) {
            int y = Integer.parseInt(req.group(1));
            if (y > requiredYears && y <= 30) requiredYears = y;
        }
        a.jobHasExperienceRequirement = requiredYears > 0;

        boolean resumeHasExperience = hasSectionHeading(normResume, "experience")
                || hasSectionHeading(normResume, "employment")
                || hasSectionHeading(normResume, "work history");

        if (requiredYears > 0) {
            int resumeYears = detectResumeYears(normResume);
            if (resumeYears > 0) {
                return clamp((int) Math.round(resumeYears * 100.0 / requiredYears));
            }
            return resumeHasExperience ? 60 : 35;
        }

        // No explicit years requirement: infer from experience presence + seniority.
        int score = resumeHasExperience ? 75 : 45;
        String resume = normResume;
        if (containsAny(resume, "senior", "lead", "manager", "director", "head", "principal")
                && containsAny(normalize(jd), "senior", "lead", "manager", "director", "head", "principal")) {
            score += 20;
        }
        return clamp(score);
    }

    private int detectResumeYears(String normResume) {
        Matcher m = YEAR_REQ.matcher(normResume);
        int stated = 0;
        while (m.find()) {
            int y = Integer.parseInt(m.group(1));
            if (y > stated && y <= 40) stated = y;
        }
        if (stated > 0) return stated;
        // Else estimate span from year tokens (e.g. 2019 - 2024).
        Matcher years = YEAR_TOKEN.matcher(normResume);
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        while (years.find()) {
            int y = Integer.parseInt(years.group());
            min = Math.min(min, y);
            max = Math.max(max, y);
        }
        if (min != Integer.MAX_VALUE && max != Integer.MIN_VALUE && max >= min) {
            return Math.min(40, max - min);
        }
        return 0;
    }

    private int educationAlignment(String jd, String normResume, Analysis a) {
        String lowerJd = jd.toLowerCase(Locale.ROOT);
        boolean requiresEdu = false;
        for (String k : EDU_KEYWORDS) {
            if (lowerJd.contains(k)) { requiresEdu = true; break; }
        }
        a.jobHasEducationRequirement = requiresEdu;
        if (!requiresEdu) return 100;

        boolean resumeHasEduSection = hasSectionHeading(normResume, "education")
                || hasSectionHeading(normResume, "academic");
        boolean resumeHasEduKeyword = false;
        for (String k : EDU_KEYWORDS) {
            if (normResume.contains(k)) { resumeHasEduKeyword = true; break; }
        }
        if (resumeHasEduSection && resumeHasEduKeyword) return 100;
        if (resumeHasEduSection || resumeHasEduKeyword) return 65;
        return 30;
    }

    private boolean hasSectionHeading(String normResume, String heading) {
        return indexOfPhrase(normResume, normalize(heading)) >= 0;
    }

    // ── Normalisation / matching primitives ───────────────────────────────────

    static String normalize(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9+#.]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    /** Whole-token phrase search on normalised text (works for c++, c#, .net …). */
    static int indexOfPhrase(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) return -1;
        int from = 0;
        while (from <= haystack.length() - needle.length()) {
            int i = haystack.indexOf(needle, from);
            if (i < 0) return -1;
            boolean leftOk = i == 0 || !isWordChar(haystack.charAt(i - 1));
            int end = i + needle.length();
            boolean rightOk = end >= haystack.length() || !isWordChar(haystack.charAt(end));
            if (leftOk && rightOk) return i;
            from = i + 1;
        }
        return -1;
    }

    private int countPhrase(String haystack, String needle) {
        int count = 0, from = 0;
        while (from <= haystack.length() - needle.length()) {
            int i = haystack.indexOf(needle, from);
            if (i < 0) break;
            boolean leftOk = i == 0 || !isWordChar(haystack.charAt(i - 1));
            int end = i + needle.length();
            boolean rightOk = end >= haystack.length() || !isWordChar(haystack.charAt(end));
            if (leftOk && rightOk) count++;
            from = i + needle.length();
        }
        return count;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '+' || c == '#';
    }

    private boolean containsAny(String text, String... words) {
        for (String w : words) if (indexOfPhrase(text, w) >= 0) return true;
        return false;
    }

    private int positionBoost(int index, int total) {
        double ratio = index / (double) total;
        if (ratio < 0.15) return 10;
        if (ratio < 0.40) return 5;
        return 0;
    }

    private int frequencyBoost(int freq) {
        return Math.min(5, Math.max(0, freq - 1)) * 8;
    }

    private static int pct(int part, int total) {
        if (total <= 0) return 100;
        return clamp((int) Math.round(part * 100.0 / total));
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(100, v));
    }

    /** Distinct canonical skill keywords known to the lexicon (for prompt hints). */
    public Set<String> knownSkillNames() {
        return new LinkedHashSet<>(SKILLS.keySet());
    }
}
