package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.ResumeSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ResumeSnapshotRepository extends JpaRepository<ResumeSnapshot, Long> {
    List<ResumeSnapshot> findByResumeIdOrderByCreatedAtDesc(Long resumeId);
    Optional<ResumeSnapshot> findByIdAndResumeId(Long id, Long resumeId);
}
