package com.henheang.securityapi.service;

import com.henheang.securityapi.domain.User;
import java.util.UUID;

public interface AccountUnlockService {

    void sendUnlockEmail(User user);

    void resendUnlockEmail(String email);

    void unlockAccount(String token);

    void adminUnlock(UUID userId);
}
