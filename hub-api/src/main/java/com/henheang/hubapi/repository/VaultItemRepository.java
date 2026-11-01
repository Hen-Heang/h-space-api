package com.henheang.hubapi.repository;

import com.henheang.hubapi.domain.VaultItem;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

// Every finder takes the owner's id; there's deliberately no lookup by item id
// alone, so a request can never reach another user's row.
public interface VaultItemRepository extends JpaRepository<VaultItem, UUID> {

    List<VaultItem> findAllByUserIdOrderByUpdatedAtDesc(UUID userId);

    Optional<VaultItem> findByIdAndUserId(UUID id, UUID userId);

    long countByUserId(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from VaultItem i where i.userId = :userId")
    int deleteByUserId(UUID userId);

    // See VaultKeysetRepository#deleteForDeletedUsers.
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    "DELETE FROM vault_items WHERE user_id IN"
                            + " (SELECT id FROM users WHERE deleted_at IS NOT NULL)",
            nativeQuery = true)
    int deleteForDeletedUsers();
}
