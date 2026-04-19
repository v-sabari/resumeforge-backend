package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.Payment;
import com.resumeforge.ai.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByUserOrderByCreatedAtDesc(User user);
    Optional<Payment> findByPaymentIdAndUser(String paymentId, User user);
    Optional<Payment> findByRazorpayPaymentId(String razorpayPaymentId);

    // ── Admin revenue queries ──────────────────────────────────────────

    /** All payments, newest first — for admin payment history table. */
    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Total revenue from PAID payments (all time). */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = 'PAID'")
    BigDecimal totalRevenue();

    /** Revenue from PAID payments since a given instant. */
    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.status = 'PAID' AND p.createdAt >= :since")
    BigDecimal revenuesSince(@Param("since") Instant since);

    /** Count of PAID payments (all time). */
    long countByStatus(String status);

    /** Count of PAID payments since a given instant. */
    @Query("SELECT COUNT(p) FROM Payment p WHERE p.status = 'PAID' AND p.createdAt >= :since")
    long countPaidSince(@Param("since") Instant since);
}
