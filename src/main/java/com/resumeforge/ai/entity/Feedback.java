package com.resumeforge.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Feedback submitted through the floating "Feedback" widget.
 *
 * type   : 'bug' | 'feature' | 'general'
 * rating : 1-5 stars
 * status : 'new' on creation. Bug/feature rows are private (admin-only); only
 *          type='general' rows are surfaced publicly by the public endpoint.
 *
 * Mirrors the ContactMessage entity conventions (created_at auditing, lombok
 * builders) so it slots into the existing persistence setup unchanged.
 */
@Entity
@Table(name = "feedback")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false)
    private Integer rating;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String details;

    @Column(name = "user_name", length = 120)
    private String userName;

    @Column(name = "user_email", length = 160)
    private String userEmail;

    @Column(name = "page_url", length = 500)
    private String pageUrl;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "new";

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
