package com.henheang.securityapi.service.impl;

import com.henheang.securityapi.domain.AuditEventType;
import com.henheang.securityapi.domain.AuditLog;
import com.henheang.securityapi.repository.AuditLogRepository;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.service.AuditLogService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogServiceImpl.class);

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Override
    // A failed audit write must never roll back or block the auth flow it's
    // observing, so it runs in its own transaction and swallows errors.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditEventType eventType, UUID userId, String identifier, String details) {
        try {
            AuditLog log = new AuditLog();
            log.setEventType(eventType);
            // A reference proxy, not a query - the caller already knows this
            // user exists (it just saved/loaded it), so there's no need to
            // re-fetch it just to populate the FK column.
            log.setUser(userId == null ? null : userRepository.getReferenceById(userId));
            log.setIdentifier(identifier);
            log.setDetails(details);
            log.setIpAddress(RequestMetadataSupport.currentClientIp());
            log.setUserAgent(RequestMetadataSupport.currentUserAgent());
            auditLogRepository.save(log);
        } catch (Exception e) {
            logger.error(
                    "Failed to write audit log for event {}: {}", eventType, e.getMessage(), e);
        }
    }

    @Override
    public Page<AuditLog> listAuditLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    @Override
    public Page<AuditLog> listAuditLogsForUser(UUID userId, Pageable pageable) {
        return auditLogRepository.findAllByUserIdOrderByCreatedAtDesc(userId, pageable);
    }
}
