package com.henheang.securityapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// No soft delete here - "revoked" already is this entity's own logical-delete
// flag; adding a second, generic deletedAt would create two ways to express
// "this token is dead" and an easy bug (filtering one but not the other). It
// does inherit @Version from BaseEntity: concurrent refresh-token rotation
// (two requests refreshing the same token at once) is a real race, and
// optimistic locking makes the loser fail fast instead of double-issuing.
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    // SHA-256 hash of the raw token handed to the client. Only the hash is
    // persisted, so a database leak doesn't hand out usable refresh tokens.
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    // The raw, un-hashed token. Populated only in memory right after
    // generation so the caller can return it to the client - never persisted.
    @Transient private String rawToken;

    @Column(nullable = false)
    private Instant expiryDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Which device this session belongs to, if the caller identified one.
    // Schema/relationship only for now - see docs/architecture.md notes on
    // Device provisioning being a follow-up, not part of this change.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(name = "revoked", nullable = false)
    private boolean revoked;
}
