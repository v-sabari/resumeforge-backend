package com.resumeforge.ai.repository;

import com.resumeforge.ai.entity.ContactMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {
    List<ContactMessage> findByStatusOrderByCreatedAtDesc(String status);
    long countByStatus(String status);
}
