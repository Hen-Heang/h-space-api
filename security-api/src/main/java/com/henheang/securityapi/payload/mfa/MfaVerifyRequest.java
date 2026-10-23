package com.henheang.securityapi.payload.mfa;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MfaVerifyRequest {
    @NotBlank(message = "MFA token is required")
    private String mfaToken;

    @NotBlank(message = "Code is required")
    private String code;
}
