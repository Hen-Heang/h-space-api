package com.henheang.securityapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

// Explicit User<->Role bridge (instead of a bare @ManyToMany) so a role grant
// carries who/when granted it - "why does this user have ROLE_ADMIN" should
// be answerable from the data. Revoking a role soft-deletes the row so that
// history survives; (user_id, role_id) is unique only among active rows (see
// the partial index in the Flyway migration), so the same role can be
// re-granted later.
@Entity
@Table(name = "user_roles")
@SQLDelete(
        sql =
                "UPDATE user_roles SET deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@NoArgsConstructor
public class UserRole extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    // Id of the admin/user who granted this role; null for system-assigned
    // defaults (e.g. ROLE_USER at signup). Not a FK - the granter's own
    // account may later be deleted without invalidating this history record.
    @Column(name = "assigned_by")
    private UUID assignedBy;

    public UserRole(User user, Role role, UUID assignedBy) {
        this.user = user;
        this.role = role;
        this.assignedBy = assignedBy;
    }
}
