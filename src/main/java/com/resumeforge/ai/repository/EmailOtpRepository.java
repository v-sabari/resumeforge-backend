package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.EmailOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {

    Optional<EmailOtp> findTopByEmailIgnoreCaseAndPurposeOrderByCreatedAtDesc(String email, String purpose);

    List<EmailOtp> findByEmailIgnoreCaseAndPurposeAndUsedFalse(String email, String purpose);
}