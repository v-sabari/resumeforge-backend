package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    /** Used by ReferralService. */
    Optional<User> findByReferralCode(String referralCode);
    boolean existsByReferralCode(String referralCode);

    // ── Admin queries ──────────────────────────────────────────────────

    /** Paginated user list for admin panel, newest first. */
    Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Search users by name or email fragment (case-insensitive). */
    @Query("""
           SELECT u FROM User u
           WHERE LOWER(u.name)  LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
           ORDER BY u.createdAt DESC
           """)
    Page<User> searchUsers(@Param("q") String query, Pageable pageable);

    /** Total registered users. */
    long count();

    /** Users registered since a given instant. */
    long countByCreatedAtAfter(Instant since);

    /** Premium users. */
    long countByPremiumTrue();

    /** Verified (enabled) users. */
    long countByEnabledTrue();
}
