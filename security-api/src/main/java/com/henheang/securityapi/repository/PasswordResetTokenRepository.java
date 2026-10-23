package com.henheang.securityapi.repository;

import com.henheang.securityapi.domain.PasswordResetToken;
import com.henheang.securityapi.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {

    List<PasswordResetToken> findByUser(User user);

    //    Delete all expired tokens
    @Modifying
    @Query("DELETE FROM PasswordResetToken t WHERE t.expiryDateTime < ?1")
    void deleteAllExpiredTokens(LocalDateTime now);

    //    Find by token
    Optional<PasswordResetToken> findByToken(String token);
}
