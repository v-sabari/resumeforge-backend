package com.cvcraft.ai.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
@Entity @Table(name = "ad_events")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AdEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "used_for_export", nullable = false) @Builder.Default private boolean usedForExport = false;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
}
