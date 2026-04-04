package com.cvcraft.ai.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "experiences")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Experience {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false) private Resume resume;
    @Column(length = 180) private String company;
    @Column(length = 180) private String role;
    @Column(length = 180) private String location;
    @Column(name = "start_date", length = 50) private String startDate;
    @Column(name = "end_date",   length = 50) private String endDate;
    @Column(name = "bullets_json", columnDefinition = "TEXT") private String bulletsJson;
}
