package com.henheang.securityapi.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
// @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PasswordResetResponse {
    private boolean valid;
}
