package com.henheang.securityapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Permission for one email address to create an account while sign-up is invite-only. */
@Getter
@Setter
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "signup_invites")
public class SignupInvite extends BaseEntity {

    // Stored lowercase.
    @Column(name = "email", nullable = false)
    private String email;

    // Admin who sent it; plain ids rather than relations - an invite never
    // needs the user loaded, and outlives nothing it points at.
    @Column(name = "invited_by")
    private UUID invitedBy;

    // Set when an account is created with this invite.
    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "used_by")
    private UUID usedBy;

    public boolean isPending() {
        return usedAt == null;
    }
}
