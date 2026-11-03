package com.henheang.securityapi.service.impl;

import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.service.AccountLockoutService;
import com.henheang.securityapi.service.AccountUnlockService;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

// Moved unchanged out of AuthServiceImpl so the step-up check can share it.
@Service
public class AccountLockoutServiceImpl implements AccountLockoutService {

    private final UserRepository userRepository;
    private final AccountUnlockService accountUnlockService;
    private final int maxFailedAttempts;
    private final Duration lockDuration;

    public AccountLockoutServiceImpl(
            UserRepository userRepository,
            AccountUnlockService accountUnlockService,
            @Value("${app.account-lock.max-failed-attempts:5}") int maxFailedAttempts,
            @Value("${app.account-lock.lock-duration-minutes:15}") long lockDurationMinutes) {
        this.userRepository = userRepository;
        this.accountUnlockService = accountUnlockService;
        this.maxFailedAttempts = maxFailedAttempts;
        this.lockDuration = Duration.ofMinutes(lockDurationMinutes);
    }

    @Override
    public void registerFailure(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        boolean justLocked = false;
        if (attempts >= maxFailedAttempts) {
            user.setLockedUntil(Instant.now().plus(lockDuration));
            justLocked = true;
        }
        userRepository.save(user);
        if (justLocked && user.getEmail() != null) {
            accountUnlockService.sendUnlockEmail(user);
        }
    }

    @Override
    public void clear(User user) {
        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }
}
