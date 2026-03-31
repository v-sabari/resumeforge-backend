package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.AdEvent;
import com.resumeforge.ai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdEventRepository extends JpaRepository<AdEvent, Long> {
    List<AdEvent> findByUserOrderByCreatedAtDesc(User user);
    Optional<AdEvent> findTopByUserAndStatusAndUsedForExportFalseOrderByCreatedAtDesc(User user, String status);
    Optional<AdEvent> findTopByUserAndStatusOrderByCreatedAtDesc(User user, String status);
}
