package com.henheang.securityapi.payload;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class LoginRequest {

    // Email or username. Older clients send it as "email".
    @NotBlank(message = "Email or username is required")
    @JsonAlias("email")
    private String identifier;

    @NotBlank(message = "Password is required")
    private String password;
}
