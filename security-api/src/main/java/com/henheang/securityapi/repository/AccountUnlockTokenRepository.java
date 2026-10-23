package com.henheang.securityapi.repository;

import com.henheang.securityapi.domain.AccountUnlockToken;
import com.henheang.securityapi.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AccountUnlockTokenRepository extends JpaRepository<AccountUnlockToken, UUID> {

    List<AccountUnlockToken> findByUser(User user);

    @Modifying
    @Query("DELETE FROM AccountUnlockToken t WHERE t.expiryDateTime < ?1")
    void deleteAllExpiredTokens(LocalDateTime now);

    Optional<AccountUnlockToken> findByToken(String token);
}
