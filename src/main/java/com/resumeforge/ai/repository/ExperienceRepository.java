package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.Experience;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExperienceRepository extends JpaRepository<Experience, Long> {
}
