package com.henheang.securityapi.service.impl;

import com.henheang.securityapi.domain.LoginHistory;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.LoginHistoryRepository;
import com.henheang.securityapi.service.LoginHistoryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginHistoryServiceImpl implements LoginHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(LoginHistoryServiceImpl.class);

    private final LoginHistoryRepository loginHistoryRepository;

    @Override
    // Same reasoning as AuditLogServiceImpl - a failed write here must never
    // roll back or block the login it's observing.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(User user, String identifier, boolean success, String failureReason) {
        try {
            LoginHistory entry = new LoginHistory();
            entry.setUser(user);
            entry.setIdentifier(identifier);
            entry.setSuccess(success);
            entry.setFailureReason(failureReason);
            entry.setIpAddress(RequestMetadataSupport.currentClientIp());
            entry.setUserAgent(RequestMetadataSupport.currentUserAgent());
            loginHistoryRepository.save(entry);
        } catch (Exception e) {
            logger.error("Failed to write login history for {}: {}", identifier, e.getMessage(), e);
        }
    }
}
