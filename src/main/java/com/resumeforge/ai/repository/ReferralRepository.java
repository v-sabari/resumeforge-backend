package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.Referral;
import com.resumeforge.ai.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReferralRepository extends JpaRepository<Referral, Long> {

    Optional<Referral> findByReferredUser(User referredUser);
    List<Referral> findByReferrerOrderByCreatedAtDesc(User referrer);
    long countByReferrerAndStatus(User referrer, String status);
    boolean existsByReferrerAndReferredUser(User referrer, User referredUser);

    @Query("SELECT COUNT(r) FROM Referral r WHERE r.referrer = :referrer AND r.status = 'QUALIFIED'")
    long countQualifiedByReferrer(@Param("referrer") User referrer);

    // ── Admin analytics ────────────────────────────────────────────────

    long countByStatus(String status);

    @Query("SELECT COUNT(r) FROM Referral r WHERE r.status = 'QUALIFIED' AND r.qualifiedAt >= :since")
    long countQualifiedSince(@Param("since") Instant since);

    @Query("SELECT COUNT(r) FROM Referral r WHERE r.createdAt >= :since")
    long countCreatedSince(@Param("since") Instant since);

    /**
     * Top referrers by qualified referral count for admin leaderboard.
     */
    @Query("""
           SELECT r.referrer AS referrer, COUNT(r) AS qualifiedCount
           FROM Referral r
           WHERE r.status = 'QUALIFIED'
           GROUP BY r.referrer
           ORDER BY COUNT(r) DESC
           """)
    List<TopReferrerRow> topReferrers(Pageable pageable);

    /** Paginated referral list for admin overview. */
    Page<Referral> findAllByOrderByCreatedAtDesc(Pageable pageable);

    interface TopReferrerRow {
        User getReferrer();
        Long getQualifiedCount();
    }
}
