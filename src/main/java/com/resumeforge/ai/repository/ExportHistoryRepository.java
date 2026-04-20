package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.ExportHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ExportHistoryRepository extends JpaRepository<ExportHistory, Long> {
    List<ExportHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
    long countByUserId(Long userId);
    
    @Query("SELECT COUNT(e) FROM ExportHistory e WHERE e.userId = :userId AND e.createdAt >= :since")
    long countRecentExports(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
