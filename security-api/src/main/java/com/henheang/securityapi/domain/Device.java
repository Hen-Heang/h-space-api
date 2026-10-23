package com.henheang.securityapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

// Schema/relationship for "known devices" (session management, trusted-device
// UX). Not yet populated by the login flow - fingerprinting requires a
// client-side contract (which header, which hash) that's a product decision,
// not something to invent inside a domain-model change. See RefreshToken and
// LoginHistory for the (currently unused) FK hookup points.
@Entity
@Table(name = "devices")
@SQLDelete(
        sql =
                "UPDATE devices SET deleted_at = now(), version = version + 1 WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
@AllArgsConstructor
@Getter
@Setter
@NoArgsConstructor
public class Device extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Stable client-supplied or server-derived identifier for this device
    // (e.g. a hash of user-agent + client-generated installation id).
    @NotBlank(message = "Device fingerprint is required")
    @Column(name = "fingerprint", nullable = false)
    private String fingerprint;

    @Column(name = "device_name")
    private String deviceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "device_type", nullable = false)
    private DeviceType deviceType = DeviceType.UNKNOWN;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "last_ip_address")
    private String lastIpAddress;

    @Column(name = "trusted", nullable = false)
    private boolean trusted = false;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;
}
