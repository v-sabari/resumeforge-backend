package com.resumeforge.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

/**
 * User testimonial displayed on the landing page and testimonials section.
 *
 * Admin-managed: created/approved/deleted via /api/admin/testimonials.
 * Public read: GET /api/testimonials returns only approved testimonials.
 *
 * Fields:
 *   authorName   — displayed name (can be anonymised, e.g. "Priya S.")
 *   authorRole   — job title / city (e.g. "Software Engineer, Bengaluru")
 *   quote        — the testimonial text (max 400 chars)
 *   rating       — 1–5 star rating
 *   approved     — only approved testimonials are returned publicly
 *   displayOrder — lower numbers appear first in the landing page grid
 */
@Entity
@Table(name = "testimonials",
       indexes = { @Index(name = "idx_testimonials_approved_order",
                          columnList = "approved, display_order") })
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Testimonial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_name", nullable = false, length = 100)
    private String authorName;

    @Column(name = "author_role", length = 120)
    private String authorRole;

    @Column(nullable = false, length = 400)
    private String quote;

    @Column(nullable = false)
    @Builder.Default
    private int rating = 5;

    @Column(nullable = false)
    @Builder.Default
    private boolean approved = false;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 100;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
