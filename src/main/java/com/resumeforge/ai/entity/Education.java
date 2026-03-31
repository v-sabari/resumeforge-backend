package com.resumeforge.ai.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "education")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Education {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @Column(length = 220)
    private String institution;

    @Column(length = 220)
    private String degree;

    @Column(length = 180)
    private String field;

    @Column(length = 50)
    private String startDate;

    @Column(length = 50)
    private String endDate;
}
