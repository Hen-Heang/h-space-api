package com.henheang.hubapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * The trace a deleted inbox item leaves (V4__inbox_item_tombstones.sql): its id, its owner and when
 * it was deleted, never its content. It lets a rollback of the Inbox cutover replay deletes, not
 * only creates and edits. Read-only here; rows are written only through {@link
 * com.henheang.hubapi.repository.InboxItemTombstoneRepository#record}.
 */
@Entity
@Table(name = "inbox_item_tombstones")
@Getter
@NoArgsConstructor
public class InboxItemTombstone {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "deleted_at", nullable = false, insertable = false, updatable = false)
    private Instant deletedAt;
}
