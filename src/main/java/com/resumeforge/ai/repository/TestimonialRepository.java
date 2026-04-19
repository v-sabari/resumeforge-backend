package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.Testimonial;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TestimonialRepository extends JpaRepository<Testimonial, Long> {

    /** Public endpoint: only approved testimonials, sorted by displayOrder. */
    List<Testimonial> findByApprovedTrueOrderByDisplayOrderAscCreatedAtDesc();

    /** Admin endpoint: all testimonials, newest first. */
    List<Testimonial> findAllByOrderByCreatedAtDesc();
}
