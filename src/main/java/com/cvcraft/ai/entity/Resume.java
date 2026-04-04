package com.cvcraft.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "resumes")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "full_name",  length = 180) private String fullName;
    @Column(name = "role",       length = 180) private String role;
    @Column(name = "email",      length = 180) private String email;
    @Column(name = "phone",       length = 60)  private String phone;
    @Column(name = "location",   length = 180) private String location;
    @Column(name = "linkedin",   length = 255) private String linkedin;
    @Column(name = "github",     length = 255) private String github;
    @Column(name = "portfolio",  length = 255) private String portfolio;
    @Column(name = "summary",    columnDefinition = "TEXT") private String summary;
    @Column(name = "skills_json",         columnDefinition = "TEXT") private String skillsJson;
    @Column(name = "certifications_json", columnDefinition = "TEXT") private String certificationsJson;
    @Column(name = "achievements_json",   columnDefinition = "TEXT") private String achievementsJson;

    @OneToMany(mappedBy = "resume", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("id ASC")
    private List<Experience> experiences = new ArrayList<>();

    @OneToMany(mappedBy = "resume", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("id ASC")
    private List<Education> educationEntries = new ArrayList<>();

    @OneToMany(mappedBy = "resume", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    @OrderBy("id ASC")
    private List<Project> projects = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
