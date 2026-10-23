package com.henheang.securityapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

// Mutable, hard-deletable entities (e.g. RefreshToken). Entities that should
// be hidden rather than erased extend SoftDeletableEntity instead; append-only
// audit trails (LoginHistory, AuditLog) extend the lighter AuditBaseEntity.
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Optimistic lock. Concurrent updates to the same row (e.g. refresh-token
    // rotation racing itself) fail fast with OptimisticLockException instead
    // of silently clobbering each other.
    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
