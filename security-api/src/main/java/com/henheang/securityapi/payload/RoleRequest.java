package com.henheang.securityapi.payload;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RoleRequest {

    @NotBlank(message = "Role name is required")
    private String name;

    private String description;
}
