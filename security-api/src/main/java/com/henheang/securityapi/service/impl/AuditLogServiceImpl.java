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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogServiceImpl.class);

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final PlatformTransactionManager transactionManager;

    // A failed audit write must never roll back or block the auth flow it's
    // observing, so it runs in its own transaction and swallows errors.
    //
    // Inside a caller's transaction the write waits until that transaction has
    // finished: the caller may have just created the user (email-code or
    // Google sign-up), and until it commits the row is invisible to a separate
    // transaction, so audit_logs.user_id would fail its foreign key. It runs on
    // rollback too, so failures logged just before an exception still land.
    @Override
    public void log(AuditEventType eventType, UUID userId, String identifier, String details) {
        AuditLog log = new AuditLog();
        log.setEventType(eventType);
        log.setIdentifier(identifier);
        log.setDetails(details);
        log.setIpAddress(RequestMetadataSupport.currentClientIp());
        log.setUserAgent(RequestMetadataSupport.currentUserAgent());

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            write(log, userId);
                        }
                    });
        } else {
            write(log, userId);
        }
    }

    private void write(AuditLog log, UUID userId) {
        // The commit is where the insert (and any constraint failure) actually
        // happens, so it has to be inside the try, not just the save().
        try {
            save(log, userId);
        } catch (Exception e) {
            if (userId != null) {
                // The user is gone - typically created by a request that then
                // rolled back. Keep the event, identified by `identifier` only.
                logger.warn(
                        "Audit log for event {} written without user {}: {}",
                        log.getEventType(),
                        userId,
                        e.getMessage());
                write(log, null);
                return;
            }
            logger.error(
                    "Failed to write audit log for event {}: {}",
                    log.getEventType(),
                    e.getMessage(),
                    e);
        }
    }

    private void save(AuditLog log, UUID userId) {
        TransactionTemplate requiresNew = new TransactionTemplate(transactionManager);
        requiresNew.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        requiresNew.executeWithoutResult(
                status -> {
                    // A fresh entity per attempt - a failed persist leaves the
                    // previous one with an id the retry must not reuse.
                    AuditLog row = new AuditLog();
                    row.setEventType(log.getEventType());
                    row.setIdentifier(log.getIdentifier());
                    row.setDetails(log.getDetails());
                    row.setIpAddress(log.getIpAddress());
                    row.setUserAgent(log.getUserAgent());
                    // A reference proxy, not a query - the caller already knows
                    // this user exists, so there's no need to re-fetch it just
                    // to populate the FK column.
                    row.setUser(userId == null ? null : userRepository.getReferenceById(userId));
                    auditLogRepository.save(row);
                });
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
