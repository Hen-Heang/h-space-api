package com.henheang.securityapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

// Resource-based RBAC: "resource:action" (e.g. "user:read") rather than a
// free-text name, so authorization checks and admin UIs can query "everything
// this role can do to X" without parsing strings.
@Entity
@Table(name = "permissions")
@SQLDelete(
        sql =
                "UPDATE permissions SET deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
public class Permission extends SoftDeletableEntity {

    // Derived as "{resource}:{action}" by callers that create permissions -
    // kept as its own column (rather than computed) so it can be indexed and
    // used directly as a Spring Security authority string.
    @NotBlank(message = "Permission name is required")
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank(message = "Resource is required")
    @Column(name = "resource", nullable = false)
    private String resource;

    @NotBlank(message = "Action is required")
    @Column(name = "action", nullable = false)
    private String action;

    private String description;
}
