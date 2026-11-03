package com.henheang.hubapi.payload.vault;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

/** Reset deletes every item and the keyset; the literal "RESET" guards against a stray call. */
@Getter
@Setter
public class VaultResetRequest extends StrictRequest {

    @NotNull
    @Pattern(regexp = "^RESET$", message = "Type RESET to confirm deleting the whole vault.")
    private String confirm;

    /**
     * Step-up: a current authenticator code or a backup code. Verified whenever sent; required once
     * {@code hub.vault.step-up-required} is on. Not stored anywhere.
     */
    @Pattern(regexp = VaultFormats.MFA_CODE, message = "mfaCode must be a 6-digit or backup code.")
    private String mfaCode;
}
