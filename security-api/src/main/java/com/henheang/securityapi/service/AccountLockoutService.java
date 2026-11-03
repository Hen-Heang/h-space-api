package com.henheang.securityapi.service;

import com.henheang.securityapi.domain.User;

/**
 * The account lock that wrong secrets count toward: wrong passwords, wrong sign-in MFA codes and
 * wrong step-up codes (MfaStepUpService) share one counter, so switching between them doesn't buy
 * an attacker more guesses.
 */
public interface AccountLockoutService {

    /**
     * Counts one wrong secret; at the limit locks the account for a while and emails an unlock
     * link.
     */
    void registerFailure(User user);

    /** Resets the counter and any lock after a correct secret. */
    void clear(User user);
}
