package com.henheang.hubapi.payload.vault;

/**
 * Shapes of the browser-made values (docs/vault-design.md, section 5). The server can't check
 * what's inside a ciphertext, only that it's a well-formed v2 envelope - which is enough to
 * guarantee nothing readable is ever stored. Kept in sync with the CHECK constraints in
 * V1__vault.sql.
 */
public final class VaultFormats {

    /** v2.&lt;12-byte IV&gt;.&lt;ciphertext + 16-byte tag&gt;, standard base64. */
    public static final String ITEM_ENVELOPE =
            "^v2\\.[A-Za-z0-9+/]{16}\\.[A-Za-z0-9+/]{24,}={0,2}$";

    /** A wrapped 32-byte vault key: exactly 48 bytes of ciphertext + tag. */
    public static final String WRAPPED_KEY = "^v2\\.[A-Za-z0-9+/]{16}\\.[A-Za-z0-9+/]{64}$";

    /** 16 random bytes, standard base64. */
    public static final String KDF_SALT = "^[A-Za-z0-9+/]{22}==$";

    public static final String KDF_PBKDF2_SHA256 = "PBKDF2-SHA256";
    public static final int MIN_KDF_ITERATIONS = 600_000;
    public static final int MAX_KDF_ITERATIONS = 10_000_000;
    public static final int MAX_ITEM_CIPHERTEXT_LENGTH = 16_384;

    /**
     * A step-up code: 6 authenticator digits, or a backup code ({@code ABCD2-EFGH3}, case and dash
     * optional). Only the shape is checked here; MfaStepUpService checks the value.
     */
    public static final String MFA_CODE = "^(\\d{6}|[A-Za-z0-9]{5}-?[A-Za-z0-9]{5})$";

    private VaultFormats() {}
}
