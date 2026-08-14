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

    // SEC/BUS FIX: needed for GDPR account deletion. A user can appear in this
    // table as either the referrer (inviter) or the referred (invitee), so both
    // directions must be cleared before the user row can be removed.
    long deleteByReferrerUser(User referrerUser);
    long deleteByReferredUser(User referredUser);
}
