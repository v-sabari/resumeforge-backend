package com.cvcraft.ai.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDateTime;
@Entity @Table(name = "export_usage")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ExportUsage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "export_count", nullable = false) private int exportCount;
    @Column(name = "ad_completed", nullable = false) private boolean adCompleted;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
}
