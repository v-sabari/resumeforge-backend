# Summary paragraph preservation in DOCX / TXT exports — audit

**Scope:** the resume **Summary** field in the server-side exports
(`GET /api/export/download/{id}/docx`, `/txt`).

**Status:** ✅ DOCX fixed and verified; TXT already correct and now test-locked.

---

## 1. Root cause (file:line)

A summary can contain `\n` paragraph breaks. `ExportService.dBody(...)` — the
helper that writes free-text body copy for DOCX — wrote the **entire** value into
a single run:

```java
// before
private void dBody(XWPFDocument d, String t) {
    if (t == null || t.isBlank()) return;
    XWPFParagraph p = d.createParagraph(); p.setSpacingAfter(60);
    XWPFRun r = p.createRun(); r.setText(t); r.setFontSize(10);
}
```

Word does not treat `\n` inside a run's text as a line break, so every paragraph
of the summary ran together.

The TXT renderer was already correct — `ExportService.java:694` appends the raw
value (`t.append(resume.getSummary()).append("\n\n");`) — so no change was made
there.

## 2. Fix

`src/main/java/com/resumeforge/ai/service/ExportService.java` `dBody` now emits
one run per line with a hard line break between them:

```java
private void dBody(XWPFDocument d, String t) {
    if (t == null || t.isBlank()) return;
    XWPFParagraph p = d.createParagraph(); p.setSpacingAfter(60);
    String[] lines = t.split("\n", -1);
    for (int i = 0; i < lines.length; i++) {
        XWPFRun r = p.createRun();
        if (i > 0) r.addBreak();
        r.setText(lines[i]);
        r.setFontSize(10);
    }
}
```

This is shared by every DOCX body paragraph (summary, experience summary,
project description, etc.), so all free-text fields now preserve paragraph
breaks, not just the summary.

## 3. Verification

`src/test/java/com/resumeforge/ai/service/SummaryWrapRegressionTest.java` builds
a resume whose summary has **4 newlines / 5 non-empty lines**, including an
accented/Unicode line, then:

- **DOCX** — unzips `word/document.xml` and asserts the number of `<w:br`
  elements equals the number of source newlines (4), and that the first two
  paragraphs are present.
- **TXT** — asserts the extracted text still contains the blank-line-separated
  paragraphs.

```
$ mvn.cmd -B test
Tests run: 50, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
  com.resumeforge.ai.service.SummaryWrapRegressionTest  Tests run: 2, Failures: 0
```

**Negative control:** with the `dBody` fix reverted,
`docxPreservesSummaryParagraphBreaks` fails `expected: <4> but was: <0>` (zero
breaks) while the TXT test still passes — proving the test actually catches the
regression and that TXT was never broken.

## 4. Limitations

- No Word / LibreOffice / Pandoc / ImageMagick is installed, so the DOCX was not
  opened in a word processor; verification is by inspecting `word/document.xml`
  (programmatic), which is exactly what Word reads.
- The legacy in-process PDF renderer
  (`ExportService.exportToPdfLegacy`, ~line 247) is deprecated and not wired to
  any controller; the reachable PDF path renders through the frontend render
  service, so template-level fixes are covered by the frontend audit
  (`resumeforge-frontend-master/docs/qa/summary-wrap-audit.md`).
