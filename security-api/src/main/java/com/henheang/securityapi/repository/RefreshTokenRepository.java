// 2. Create a repository for managing refresh tokens
package com.henheang.securityapi.repository;

import com.henheang.securityapi.domain.RefreshToken;
import com.henheang.securityapi.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserAndRevokedFalse(User user);

    List<RefreshToken> findAllByUser(User user);

    // Revokes the token only if it's still active, in one statement: of two
    // concurrent refreshes with the same token, exactly one gets 1 back.
    @Modifying
    @Query(
            "UPDATE RefreshToken r SET r.revoked = true, r.version = r.version + 1"
                    + " WHERE r.id = :id AND r.revoked = false")
    int revokeIfActive(@Param("id") UUID id);

    //    @Modifying
    //    @Query("UPDATE RefreshToken r SET r.revoked = true WHERE r.user = :user")
    //    void revokeAllUserTokens(User user);
    //
    //    @Modifying
    //    @Query("DELETE FROM RefreshToken r WHERE r.expiryDate < :now")
    //    void deleteAllExpiredTokens(Instant now);
}
