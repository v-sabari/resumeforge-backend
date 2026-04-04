package com.cvcraft.ai.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "projects")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Project {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false) private Resume resume;
    @Column(length = 220) private String name;
    @Column(length = 255) private String link;
    @Column(columnDefinition = "TEXT") private String description;
}
