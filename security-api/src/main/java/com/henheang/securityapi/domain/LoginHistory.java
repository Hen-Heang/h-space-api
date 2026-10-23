package com.henheang.securityapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// UX-facing "recent sign-ins" trail - narrower than AuditLog (which covers
// the full compliance event set: signup, MFA changes, password resets, etc).
// Kept as its own table rather than folded into AuditLog so the
// (user_id, created_at) / device-join query pattern for a "sessions" screen
// doesn't have to compete with the broader audit trail's indexing needs.
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "login_history")
public class LoginHistory extends AuditBaseEntity {

    // Nullable - a failed login for an unknown/unresolved identifier has no
    // user row to attach to; the raw identifier is kept below regardless.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    private Device device;

    @Column(name = "identifier")
    private String identifier;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "success", nullable = false)
    private boolean success;

    @Column(name = "failure_reason")
    private String failureReason;
}
