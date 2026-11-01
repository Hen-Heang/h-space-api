package com.henheang.hubapi.payload.vault;

import com.henheang.hubapi.domain.VaultItem;
import java.time.Instant;
import java.util.UUID;

public record VaultItemResponse(
        UUID id, String payloadCiphertext, Instant createdAt, Instant updatedAt, long version) {

    public static VaultItemResponse from(VaultItem item) {
        return new VaultItemResponse(
                item.getId(),
                item.getPayloadCiphertext(),
                item.getCreatedAt(),
                item.getUpdatedAt(),
                item.getVersion());
    }
}
