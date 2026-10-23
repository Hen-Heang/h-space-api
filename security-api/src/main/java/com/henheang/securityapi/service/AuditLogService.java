package com.henheang.securityapi.service;

import com.henheang.securityapi.domain.AuditEventType;
import com.henheang.securityapi.domain.AuditLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface AuditLogService {

    void log(AuditEventType eventType, UUID userId, String identifier, String details);

    default void log(AuditEventType eventType, UUID userId, String identifier) {
        log(eventType, userId, identifier, null);
    }

    Page<AuditLog> listAuditLogs(Pageable pageable);

    Page<AuditLog> listAuditLogsForUser(UUID userId, Pageable pageable);
}
