package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.Resume;
import com.resumeforge.ai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    List<Resume> findByUserOrderByUpdatedAtDesc(User user);

    Optional<Resume> findByIdAndUser(Long id, User user);

    /** Used by ResumeService.create() to detect whether this is the user's first resume. */
    long countByUser(User user);
}
