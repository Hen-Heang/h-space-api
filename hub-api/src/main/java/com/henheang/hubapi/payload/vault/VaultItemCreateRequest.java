package com.henheang.hubapi.payload.vault;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** The id is chosen by the browser: it's part of the ciphertext's associated data. */
@Getter
@Setter
public class VaultItemCreateRequest extends StrictRequest {

    @NotNull private UUID id;

    @NotNull
    @Size(
            max = VaultFormats.MAX_ITEM_CIPHERTEXT_LENGTH,
            message = "payloadCiphertext is too large.")
    @Pattern(
            regexp = VaultFormats.ITEM_ENVELOPE,
            message = "payloadCiphertext must be a v2 encrypted envelope.")
    private String payloadCiphertext;
}
