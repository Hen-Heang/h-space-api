package com.henheang.hubapi.payload.vault;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/**
 * Creates the keyset (no {@code version}) or replaces it after a master-password change or
 * recovery-key rotation ({@code version} of the keyset being replaced).
 */
@Getter
@Setter
public class VaultKeysetRequest extends StrictRequest {

    @NotNull
    @Pattern(regexp = "^PBKDF2-SHA256$", message = "kdf must be PBKDF2-SHA256.")
    private String kdf;

    @NotNull
    @Min(
            value = VaultFormats.MIN_KDF_ITERATIONS,
            message = "kdfIterations must be at least 600000.")
    @Max(value = VaultFormats.MAX_KDF_ITERATIONS, message = "kdfIterations is too large.")
    private Integer kdfIterations;

    @NotNull
    @Pattern(regexp = VaultFormats.KDF_SALT, message = "kdfSalt must be 16 bytes of base64.")
    private String kdfSalt;

    @NotNull
    @Pattern(regexp = VaultFormats.WRAPPED_KEY, message = "wrappedKey must be a v2 wrapped key.")
    private String wrappedKey;

    @NotNull
    @Pattern(
            regexp = VaultFormats.WRAPPED_KEY,
            message = "recoveryWrappedKey must be a v2 wrapped key.")
    private String recoveryWrappedKey;

    private Long version;

    /**
     * Step-up for <em>replacing</em> a keyset (master-password change, recovery): a current
     * authenticator code or a backup code. Not needed to create the first keyset, which can't lose
     * anything. Verified whenever sent; required on replacement once {@code
     * hub.vault.step-up-required} is on. Not stored anywhere.
     */
    @Pattern(regexp = VaultFormats.MFA_CODE, message = "mfaCode must be a 6-digit or backup code.")
    private String mfaCode;
}
