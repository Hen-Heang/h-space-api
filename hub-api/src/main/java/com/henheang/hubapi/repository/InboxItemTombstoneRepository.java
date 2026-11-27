package com.henheang.hubapi.repository;

import com.henheang.hubapi.domain.InboxItemTombstone;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InboxItemTombstoneRepository extends JpaRepository<InboxItemTombstone, UUID> {

    /**
     * Records that the caller's item {@code id} was deleted. Must run inside the transaction that
     * deletes the item, after the ownership check, so the two commit together. {@code deleted_at}
     * is the database's clock ({@code now()}, the transaction's start time).
     *
     * <p>{@code ON CONFLICT DO NOTHING} is defensive: the delete runs first and is version-checked,
     * so a racing second delete of the same item fails before reaching this insert. If an id ever
     * did repeat, the first tombstone is kept rather than failing the delete.
     *
     * @return 1 if a row was written, 0 if one already existed
     */
    @Modifying
    @Query(
            value =
                    "INSERT INTO inbox_item_tombstones (id, user_id) VALUES (:id, :userId)"
                            + " ON CONFLICT (id) DO NOTHING",
            nativeQuery = true)
    int record(@Param("id") UUID id, @Param("userId") UUID userId);

    List<InboxItemTombstone> findByUserId(UUID userId);
}
