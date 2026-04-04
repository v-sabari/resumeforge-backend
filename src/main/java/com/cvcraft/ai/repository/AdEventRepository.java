package com.cvcraft.ai.repository;
import com.cvcraft.ai.entity.AdEvent;
import com.cvcraft.ai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface AdEventRepository extends JpaRepository<AdEvent, Long> {
    Optional<AdEvent> findTopByUserAndStatusOrderByCreatedAtDesc(User user, String status);
    Optional<AdEvent> findTopByUserAndStatusAndUsedForExportFalseOrderByCreatedAtDesc(User user, String status);
}
