package com.henheang.securityapi.repository;

import com.henheang.securityapi.domain.RevokedToken;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RevokedTokenRepository extends JpaRepository<RevokedToken, UUID> {
    boolean existsByJti(String jti);

    // One bulk DELETE - not load-then-delete-each-row.
    @Modifying
    @Query("DELETE FROM RevokedToken t WHERE t.expiryDate < :now")
    int deleteAllExpiredTokens(@Param("now") Instant now);
}
