package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.Feedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    /**
     * Public listing: only general feedback, newest first.
     * Bug reports and feature requests are intentionally excluded here.
     */
    Page<Feedback> findByTypeOrderByCreatedAtDesc(String type, Pageable pageable);

    /**
     * Average star rating for the public section header.
     * Returns null when there are no matching rows.
     */
    @Query("select avg(f.rating) from Feedback f where f.type = :type")
    Double averageRatingByType(@Param("type") String type);
}
