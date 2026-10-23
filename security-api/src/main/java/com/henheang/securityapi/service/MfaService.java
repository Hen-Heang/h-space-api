package com.henheang.securityapi.service;

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
}
