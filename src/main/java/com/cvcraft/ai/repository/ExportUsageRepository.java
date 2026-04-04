package com.cvcraft.ai.repository;
import com.cvcraft.ai.entity.ExportUsage;
import com.cvcraft.ai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ExportUsageRepository extends JpaRepository<ExportUsage, Long> {
    long countByUser(User user);
}
