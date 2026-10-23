package com.henheang.securityapi.payload.mfa;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MfaSetupResponse {
    private String secret;
    private String otpAuthUri;

    // Base64-encoded PNG, ready to render as `data:image/png;base64,...` -
    // null if QR rendering failed (caller falls back to manual entry via
    // secret/otpAuthUri).
    private String qrCodeImageBase64;
}
