package com.henheang.securityapi.service;

import java.util.OptionalLong;

public interface MfaService {

    String generateSecret();

    String getOtpAuthUri(String accountEmail, String secret);

    // PNG QR code (Base64-encoded, ready to prefix as a data: URI) that
    // encodes the same otpauth:// URI as getOtpAuthUri - what the user
    // actually scans into Google Authenticator / Authy / any TOTP-compatible
    // app. Returns null if rendering fails; callers should fall back to the
    // otpauth:// URI (manual entry) rather than fail setup outright.
    String generateQrCodeImageBase64(String accountEmail, String secret);

    boolean verifyCode(String secret, String code);

    // Like verifyCode, but answers which TOTP time step the code belongs to,
    // so sign-in can refuse a code that was already used.
    OptionalLong matchTimeStep(String secret, String code);
}
