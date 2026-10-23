package com.henheang.securityapi.service;

import java.time.Instant;

public interface TokenBlacklistService {
    void revoke(String jti, Instant expiryDate);

    boolean isRevoked(String jti);
}
