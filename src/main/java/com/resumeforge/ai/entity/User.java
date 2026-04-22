package com.resumeforge.ai.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "is_premium", nullable = false)
    @Builder.Default
    private boolean premium = false;

    @Column(name = "premium_expires_at")
    private Instant premiumExpiresAt;

    @Column(name = "referral_code", unique = true, length = 12)
    private String referralCode;

    @Column(name = "has_created_resume", nullable = false)
    @Builder.Default
    private boolean hasCreatedResume = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Resume> resumes = new ArrayList<>();
}
