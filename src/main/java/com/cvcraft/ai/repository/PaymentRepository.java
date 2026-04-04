package com.cvcraft.ai.repository;
import com.cvcraft.ai.entity.Payment;
import com.cvcraft.ai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPaymentIdAndUser(String paymentId, User user);
    Optional<Payment> findByPaymentId(String paymentId);
    List<Payment> findByUserOrderByCreatedAtDesc(User user);
}
