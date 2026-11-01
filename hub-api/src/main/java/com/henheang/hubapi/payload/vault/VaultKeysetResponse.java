package com.henheang.hubapi.payload.vault;

import com.henheang.hubapi.domain.VaultKeyset;
import java.time.Instant;

public record VaultKeysetResponse(
        String kdf,
        int kdfIterations,
        String kdfSalt,
        String wrappedKey,
        String recoveryWrappedKey,
        Instant createdAt,
        Instant updatedAt,
        long version) {

    public static VaultKeysetResponse from(VaultKeyset keyset) {
        return new VaultKeysetResponse(
                keyset.getKdf(),
                keyset.getKdfIterations(),
                keyset.getKdfSalt(),
                keyset.getWrappedKey(),
                keyset.getRecoveryWrappedKey(),
                keyset.getCreatedAt(),
                keyset.getUpdatedAt(),
                keyset.getVersion());
    }
}
