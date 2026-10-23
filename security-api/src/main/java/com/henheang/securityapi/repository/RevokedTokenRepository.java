package com.henheang.securityapi.repository;

import com.henheang.securityapi.domain.RevokedToken;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, UUID> {
    boolean existsByJti(String jti);

    List<RevokedToken> findAllByExpiryDateBefore(Instant now);
}
