package com.henheang.hubapi.repository;

import com.henheang.hubapi.domain.VaultKeyset;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

// Keyed by user id, so findById(userId) is already owner-scoped.
public interface VaultKeysetRepository extends JpaRepository<VaultKeyset, UUID> {

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from VaultKeyset k where k.userId = :userId")
    int deleteByUserId(UUID userId);

    // Users are soft-deleted (security-api), so ON DELETE CASCADE never fires
    // for them; VaultCleanupTask calls this instead.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    "DELETE FROM vault_keysets WHERE user_id IN"
                            + " (SELECT id FROM users WHERE deleted_at IS NOT NULL)",
            nativeQuery = true)
    int deleteForDeletedUsers();
}
