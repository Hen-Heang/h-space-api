package com.henheang.hubapi.payload.vault;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Replaces the whole encrypted item. {@code version} is the one the client last read; a different
 * current version means another device changed it first (409).
 */
@Getter
@Setter
public class VaultItemUpdateRequest extends StrictRequest {

    @NotNull
    @Size(
            max = VaultFormats.MAX_ITEM_CIPHERTEXT_LENGTH,
            message = "payloadCiphertext is too large.")
    @Pattern(
            regexp = VaultFormats.ITEM_ENVELOPE,
            message = "payloadCiphertext must be a v2 encrypted envelope.")
    private String payloadCiphertext;

    @NotNull private Long version;
}
