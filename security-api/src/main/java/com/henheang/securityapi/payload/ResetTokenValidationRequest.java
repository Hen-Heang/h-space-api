package com.henheang.securityapi.payload;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Body of POST /api/auth/reset-password/validate. The reset token travels in a
// body, not a query string, so it stays out of access logs, proxies and the
// Referer header.
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class ResetTokenValidationRequest {

    @NotBlank(message = "Token is required")
    private String token;
}
