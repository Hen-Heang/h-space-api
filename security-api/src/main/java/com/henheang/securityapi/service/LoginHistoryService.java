package com.henheang.securityapi.service;

import com.henheang.securityapi.domain.User;

public interface LoginHistoryService {

    void record(User user, String identifier, boolean success, String failureReason);

    default void recordSuccess(User user, String identifier) {
        record(user, identifier, true, null);
    }

    // No user attached - the identifier didn't resolve to an account.
    default void recordFailure(String identifier, String failureReason) {
        record(null, identifier, false, failureReason);
    }
}
