package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.ReferralHistory;
import com.resumeforge.ai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReferralHistoryRepository extends JpaRepository<ReferralHistory, Long> {
    List<ReferralHistory> findByReferrerUserOrderByCreatedAtDesc(User referrerUser);
    Optional<ReferralHistory> findByReferredUser(User referredUser);
    long countByReferrerUserAndStatus(User referrerUser, ReferralHistory.ReferralStatus status);
}
