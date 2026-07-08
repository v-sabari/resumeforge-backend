package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Payment> findByRazorpayOrderId(String orderId);
    Optional<Payment> findByRazorpayPaymentId(String paymentId);
    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByStatus(String status);
    void deleteByUserId(Long userId);

    // NEW: revenue figures for overview tab
    long countByStatusAndCreatedAtAfter(String status, LocalDateTime after);

    @Query("SELECT COALESCE(SUM(p.amount),0) FROM Payment p WHERE p.status = 'COMPLETED'")
    BigDecimal sumCompletedAmount();

    @Query("SELECT COALESCE(SUM(p.amount),0) FROM Payment p WHERE p.status = 'COMPLETED' AND p.createdAt > :after")
    BigDecimal sumCompletedAmountAfter(@Param("after") LocalDateTime after);
}