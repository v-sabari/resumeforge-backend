package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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