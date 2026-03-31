package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.Payment;
import com.resumeforge.ai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByUserOrderByCreatedAtDesc(User user);
    Optional<Payment> findByPaymentIdAndUser(String paymentId, User user);
}
