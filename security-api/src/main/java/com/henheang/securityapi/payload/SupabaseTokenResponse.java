package com.henheang.securityapi.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class SupabaseTokenResponse {
    private String accessToken;
    private Long expiresIn;
    private String tokenType = "Bearer";

    public SupabaseTokenResponse(String accessToken, Long expiresIn) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
    }
}
