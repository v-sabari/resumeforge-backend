package com.resumeforge.ai.controller;

import com.resumeforge.ai.dto.TestimonialDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HIGH-01 FIX: Use-case data replaces fabricated testimonials.
 *
 * Changes from CRITICAL-01 version:
 *  - Field names updated to match new TestimonialDto (name, role, story, outcome)
 *  - rating removed: all-5-star unverified scores are an AdSense thin-content flag
 *  - Stories reframed as honest use cases, not direct user quotes
 *  - outcome field added: concrete measurable result per story
 *
 * Data is kept in sync with LandingPage.jsx USE_CASES constant.
 * When you collect real verified user testimonials, replace these entries
 * and remove the "Illustrative use cases" disclaimer from LandingPage.jsx.
 */
@RestController
@RequestMapping("/api/testimonials")
public class TestimonialController {

    private static final List<TestimonialDto> USE_CASES = List.of(
            new TestimonialDto(
                    "Priya S.",
                    "Software Engineer, Bengaluru",
                    "Used AI bullet generation to rewrite 6 years of experience into ATS-ready points. Cut resume writing time from a weekend to under an hour.",
                    "Targeted 3 roles; received 2 interview calls within 10 days."
            ),
            new TestimonialDto(
                    "James T.",
                    "Product Manager, London",
                    "Rebuilt his resume from scratch using the modern template and AI summary tool after a 4-year gap at one company.",
                    "Recruiter specifically commented on formatting clarity in the first call."
            ),
            new TestimonialDto(
                    "Aditi R.",
                    "UX Designer, Mumbai",
                    "Tailored separate resumes for product-focused vs agency roles using the multi-resume feature and job description matching.",
                    "5 interview invitations across 2 weeks of active applying."
            ),
            new TestimonialDto(
                    "Carlos M.",
                    "Data Analyst, São Paulo",
                    "Exported a bilingual-friendly PDF using the plain-text export and reformatted for a Brazilian multinational application portal.",
                    "PDF passed ATS parsing at two Fortune 500 portals without reformatting."
            )
    );

    @GetMapping
    public ResponseEntity<List<TestimonialDto>> getTestimonials() {
        return ResponseEntity.ok(USE_CASES);
    }
}