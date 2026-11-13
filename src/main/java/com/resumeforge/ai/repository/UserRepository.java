package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByPasswordResetToken(String token);
    Optional<User> findByReferralCode(String referralCode);
    Optional<User> findByGoogleId(String googleId);

    /**
     * EXPORT QUOTA FIX: pessimistic row lock used to serialise the
     * count-then-insert sequence in {@code ExportQuotaService}. Two concurrent
     * downloads for the same user could otherwise both read the same
     * "exports used" count and both insert, letting a free user exceed the
     * daily cap. Locking the owning user row makes the check + insert atomic.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    boolean existsByEmail(String email);

    long countByPremiumTrue();
    long countByEmailVerifiedTrue();

    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.name) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%'))")
    Page<User> searchUsers(@Param("query") String query, Pageable pageable);

    @Query("SELECT COUNT(u) FROM User u WHERE u.referredByUserId = :userId AND u.emailVerified = true")
    long countVerifiedReferrals(@Param("userId") Long userId);

    // NEW: overview tab growth metric
    long countByCreatedAtAfter(Instant after);

    // NEW: referral stats — "qualified" = referred user's email is verified,
    // matching the existing countVerifiedReferrals convention above.
    long countByReferredByUserIdIsNotNull();

    long countByReferredByUserIdIsNotNullAndEmailVerifiedTrue();

    long countByReferredByUserIdIsNotNullAndEmailVerifiedFalse();

    long countByReferredByUserIdIsNotNullAndEmailVerifiedTrueAndCreatedAtAfter(Instant after);

    @Query("SELECT u.referredByUserId, COUNT(u) FROM User u " +
            "WHERE u.referredByUserId IS NOT NULL AND u.emailVerified = true " +
            "GROUP BY u.referredByUserId ORDER BY COUNT(u) DESC")
    List<Object[]> getTopReferrerCounts(Pageable pageable);
}