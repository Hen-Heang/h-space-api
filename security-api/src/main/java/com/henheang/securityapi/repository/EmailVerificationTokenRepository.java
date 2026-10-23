package com.henheang.securityapi.repository;

import com.henheang.securityapi.domain.EmailVerificationToken;
import com.henheang.securityapi.domain.User;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface EmailVerificationTokenRepository
        extends JpaRepository<EmailVerificationToken, UUID> {

    List<EmailVerificationToken> findByUser(User user);

    @Modifying
    @Query("DELETE FROM EmailVerificationToken t WHERE t.expiryDateTime < ?1")
    void deleteAllExpiredTokens(LocalDateTime now);

    Optional<EmailVerificationToken> findByToken(String token);
}
