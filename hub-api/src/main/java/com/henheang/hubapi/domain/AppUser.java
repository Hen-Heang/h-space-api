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
 * The local anchor that hub rows reference (V3__app_users.sql): a user id and when it was first
 * seen, nothing else. Not admission, not a profile, never a password. Written only through {@link
 * com.henheang.hubapi.repository.AppUserRepository#ensure}.
 */
@Entity
@Table(name = "app_users")
@Getter
@NoArgsConstructor
public class AppUser {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
