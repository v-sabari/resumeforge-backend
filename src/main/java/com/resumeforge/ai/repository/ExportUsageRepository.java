package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.ExportUsage;
import com.resumeforge.ai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExportUsageRepository extends JpaRepository<ExportUsage, Long> {
    List<ExportUsage> findByUserOrderByCreatedAtDesc(User user);
    Optional<ExportUsage> findTopByUserOrderByCreatedAtDesc(User user);
    long countByUser(User user);
}
