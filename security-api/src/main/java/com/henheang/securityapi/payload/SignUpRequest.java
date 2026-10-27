package com.henheang.securityapi.payload;

import com.henheang.securityapi.validation.StrongPassword;
import com.henheang.securityapi.validation.UsernameRules;
import com.henheang.securityapi.validation.ValidIdentifier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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

    // Required: users.email is NOT NULL, and it's how password reset and
    // verification reach the account.
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    private String phoneNumber; // Optional

    // Minimum length comes from @StrongPassword (app.password-policy.min-length).
    @NotBlank(message = "Password is required")
    @Size(max = 72, message = "Password must be at most 72 characters long")
    @StrongPassword
    private String password;

    @NotBlank(message = "Username is required")
    @Pattern(regexp = UsernameRules.PATTERN, message = UsernameRules.MESSAGE)
    private String username;

    // Custom validation method to ensure at least one of email or phone is provided
    public boolean hasValidIdentifier() {
        return (email != null && !email.trim().isEmpty())
                || (phoneNumber != null && !phoneNumber.trim().isEmpty());
    }
}
