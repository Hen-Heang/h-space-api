package com.henheang.securityapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Broad compliance/audit trail (signup, login, MFA changes, password resets,
// token revocation - see AuditEventType). Renamed from the previous
// "AuditEvent" to match AuditLogService, which was already named for this.
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_logs")
public class AuditLog extends AuditBaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private AuditEventType eventType;

    // Nullable - a failed login for an unknown identifier never resolves to a
    // user row. Whenever this is non-null, the referenced user is known to
    // have existed at writing time, so it's a real FK (unlike the old
    // AuditEvent.userId, which was a bare unconstrained column).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // The identifier the caller supplied (e.g. email on a login attempt),
    // kept even when it doesn't resolve to a real user - that's exactly the
    // case an auditor most wants a record of.
    @Column(name = "identifier")
    private String identifier;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "details")
    private String details;
}
