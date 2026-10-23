package com.henheang.securityapi.payload;

import com.henheang.securityapi.validation.StrongPassword;
import com.henheang.securityapi.validation.ValidIdentifier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Data
@ValidIdentifier
public class SignUpRequest {

    @NotBlank(message = "Name is required")
    private String name;

    @Email(message = "Email should be valid")
    private String email; // Optional

    private String phoneNumber; // Optional

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters long")
    @StrongPassword
    private String password;

    // Custom validation method to ensure at least one of email or phone is provided
    public boolean hasValidIdentifier() {
        return (email != null && !email.trim().isEmpty())
                || (phoneNumber != null && !phoneNumber.trim().isEmpty());
    }
}
