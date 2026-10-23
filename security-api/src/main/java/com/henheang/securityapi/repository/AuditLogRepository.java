package com.henheang.securityapi.repository;

import com.henheang.securityapi.domain.AuditLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);
}
