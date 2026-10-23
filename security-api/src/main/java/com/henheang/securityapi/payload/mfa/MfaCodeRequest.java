package com.henheang.securityapi.payload.mfa;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MfaCodeRequest {
    @NotBlank(message = "Code is required")
    private String code;
}
