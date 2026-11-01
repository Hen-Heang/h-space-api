package com.henheang.hubapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/**
 * One saved account. Everything about it - service, login, URL, password - is inside {@code
 * payloadCiphertext}, encrypted in the browser with the item id as associated data. The id is
 * therefore assigned by the client, not generated here.
 */
@Entity
@Table(name = "vault_items")
@Getter
@Setter
public class VaultItem {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "payload_ciphertext", nullable = false, length = 16384)
    private String payloadCiphertext;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
