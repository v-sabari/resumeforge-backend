package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRepository extends JpaRepository<Project, Long> {
}
