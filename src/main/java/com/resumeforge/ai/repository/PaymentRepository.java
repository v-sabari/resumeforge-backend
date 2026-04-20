package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<Payment> findByRazorpayOrderId(String orderId);
    Optional<Payment> findByRazorpayPaymentId(String paymentId);
    Page<Payment> findAllByOrderByCreatedAtDesc(Pageable pageable);
    long countByStatus(String status);
}
