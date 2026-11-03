package com.henheang.securityapi.service.impl;

import com.henheang.securityapi.domain.AuditEventType;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.service.AccountLockoutService;
import com.henheang.securityapi.service.AuditLogService;
import com.henheang.securityapi.service.MfaBackupCodeService;
import com.henheang.securityapi.service.MfaService;
import com.henheang.securityapi.service.MfaStepUpService;
import java.util.OptionalLong;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class MfaStepUpServiceImpl implements MfaStepUpService {

    private final UserRepository userRepository;
    private final MfaService mfaService;
    private final MfaBackupCodeService mfaBackupCodeService;
    private final AccountLockoutService accountLockoutService;
    private final AuditLogService auditLogService;

    // Its own transaction: callers reject the action by throwing, which rolls
    // their transaction back. The failed-attempt count, the claimed time step
    // and the audit entry must survive that, or wrong codes would never lock.
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Result verify(UUID userId, String code, String action) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isMfaEnabled()) {
            return Result.MFA_NOT_ENABLED;
        }
        if (!user.isAccountNonLocked()) {
            return Result.LOCKED;
        }
        if (StringUtils.hasText(code) && matches(user, code)) {
            accountLockoutService.clear(user);
            auditLogService.log(
                    AuditEventType.MFA_STEP_UP_VERIFIED, user.getId(), user.getEmail(), action);
            return Result.VERIFIED;
        }
        accountLockoutService.registerFailure(user);
        auditLogService.log(
                AuditEventType.MFA_STEP_UP_FAILED, user.getId(), user.getEmail(), action);
        return user.isAccountNonLocked() ? Result.INVALID : Result.LOCKED;
    }

    private boolean matches(User user, String code) {
        // An authenticator code is accepted once: its time step must be later
        // than the last one used anywhere (sign-in or step-up). A replay falls
        // through and counts as a wrong code.
        OptionalLong step = mfaService.matchTimeStep(user.getMfaSecret(), code);
        if (step.isPresent()
                && userRepository.claimMfaTimeStep(user.getId(), step.getAsLong()) == 1) {
            user.setMfaLastUsedStep(step.getAsLong());
            return true;
        }
        if (mfaBackupCodeService.verifyAndConsume(user, code)) {
            auditLogService.log(AuditEventType.MFA_BACKUP_CODE_USED, user.getId(), user.getEmail());
            return true;
        }
        return false;
    }
}
