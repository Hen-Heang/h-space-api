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
 * A user's wrapped vault key (docs/vault-design.md, section 5). Holds nothing the server can use to
 * decrypt: the KEK is derived in the browser from the master password or recovery key, and neither
 * ever reaches this service.
 */
@Entity
@Table(name = "vault_keysets")
@Getter
@Setter
public class VaultKeyset {

    @Id
    @Column(name = "user_id", updatable = false, nullable = false)
    private UUID userId;

    @Column(name = "kdf", nullable = false, length = 32)
    private String kdf;

    @Column(name = "kdf_iterations", nullable = false)
    private int kdfIterations;

    @Column(name = "kdf_salt", nullable = false, length = 24)
    private String kdfSalt;

    @Column(name = "wrapped_key", nullable = false, length = 84)
    private String wrappedKey;

    @Column(name = "recovery_wrapped_key", nullable = false, length = 84)
    private String recoveryWrappedKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
