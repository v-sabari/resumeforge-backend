package com.resumeforge.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "export_usage")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExportUsage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "export_count", nullable = false)
    private int exportCount;

    @Column(name = "ad_completed", nullable = false)
    private boolean adCompleted;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
