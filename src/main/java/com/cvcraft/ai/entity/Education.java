package com.cvcraft.ai.entity;
import jakarta.persistence.*;
import lombok.*;
@Entity @Table(name = "education")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Education {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resume_id", nullable = false) private Resume resume;
    @Column(length = 220) private String institution;
    @Column(length = 220) private String degree;
    @Column(length = 180) private String field;
    @Column(name = "start_date", length = 50) private String startDate;
    @Column(name = "end_date",   length = 50) private String endDate;
}
