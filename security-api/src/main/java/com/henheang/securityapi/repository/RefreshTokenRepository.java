// 2. Create a repository for managing refresh tokens
package com.henheang.securityapi.repository;

import com.henheang.securityapi.domain.RefreshToken;
import com.henheang.securityapi.domain.User;
import java.time.Instant;
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

    // The user's live sessions (unrevoked, unexpired), oldest first, so the
    // service can drop the oldest when a user is over the session cap.
    @Query(
            "SELECT r FROM RefreshToken r WHERE r.user = :user AND r.revoked = false"
                    + " AND r.expiryDate > :now ORDER BY r.createdAt ASC")
    List<RefreshToken> findActiveByUserOldestFirst(
            @Param("user") User user, @Param("now") Instant now);

    // Spends the token only if it's still active, in one statement: of two
    // concurrent refreshes with the same token, exactly one gets 1 back. Also
    // records when it was spent, so a later replay is recognised as reuse.
    @Modifying
    @Query(
            "UPDATE RefreshToken r SET r.revoked = true, r.rotatedAt = :now,"
                    + " r.version = r.version + 1 WHERE r.id = :id AND r.revoked = false")
    int spendForRotation(@Param("id") UUID id, @Param("now") Instant now);

    // Ends every live token of one sign-in chain (reuse detected).
    @Modifying
    @Query(
            "UPDATE RefreshToken r SET r.revoked = true, r.version = r.version + 1"
                    + " WHERE r.familyId = :familyId AND r.revoked = false")
    int revokeFamily(@Param("familyId") UUID familyId);

    // One bulk DELETE for the cleanup job - it used to load every refresh token
    // into memory and delete them one by one.
    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiryDate < :now")
    int deleteAllExpiredTokens(@Param("now") Instant now);
}
