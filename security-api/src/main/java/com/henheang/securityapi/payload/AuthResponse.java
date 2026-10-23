package com.henheang.securityapi.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private String tokenType = "Bearer";

    // When true, accessToken/refreshToken are absent and mfaToken must be
    // exchanged (with a valid TOTP code) via /api/auth/mfa/verify instead.
    private boolean mfaRequired = false;
    private String mfaToken;

    public AuthResponse(String accessToken, String refreshToken, Long expiresIn) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
    }

    public static AuthResponse mfaRequired(String mfaToken) {
        AuthResponse response = new AuthResponse();
        response.mfaRequired = true;
        response.mfaToken = mfaToken;
        return response;
    }
}
