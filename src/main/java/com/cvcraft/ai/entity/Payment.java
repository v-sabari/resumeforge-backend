package com.cvcraft.ai.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.LocalDateTime;
@Entity @Table(name = "payments")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "payment_id", nullable = false, unique = true, length = 120) private String paymentId;
    @Column(nullable = false, precision = 10, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 40) private String status;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
}
