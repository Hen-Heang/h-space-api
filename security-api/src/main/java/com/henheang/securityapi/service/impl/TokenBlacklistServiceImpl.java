package com.henheang.securityapi.service.impl;

import com.henheang.securityapi.domain.RevokedToken;
import com.henheang.securityapi.repository.RevokedTokenRepository;
import com.henheang.securityapi.service.TokenBlacklistService;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    private final RevokedTokenRepository revokedTokenRepository;

    @Override
    @Transactional
    public void revoke(String jti, Instant expiryDate) {
        if (revokedTokenRepository.existsByJti(jti)) {
            return;
        }
        RevokedToken revokedToken = new RevokedToken();
        revokedToken.setJti(jti);
        revokedToken.setExpiryDate(expiryDate);
        revokedToken.setRevokedAt(Instant.now());
        revokedTokenRepository.save(revokedToken);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isRevoked(String jti) {
        return revokedTokenRepository.existsByJti(jti);
    }
}
