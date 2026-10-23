package com.henheang.securityapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "revoked_tokens")
public class RevokedToken extends BaseEntity {

    // JWT "jti" claim - identifies the specific access token that was revoked.
    @Column(name = "jti", nullable = false, unique = true)
    private String jti;

    // The access token's own expiration. Once this passes, the blacklist entry
    // is dead weight - the token would be rejected as expired anyway - so the
    // cleanup job purges rows past this point instead of keeping them forever.
    @Column(name = "expiry_date", nullable = false)
    private Instant expiryDate;

    @Column(name = "revoked_at", nullable = false)
    private Instant revokedAt;
}
