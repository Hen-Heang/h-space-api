package com.henheang.securityapi.service;

import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.payload.UpdateUserRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public interface UserService {

    boolean existsByEmail(
            @NotBlank(message = "Email is required") @Email(message = "Email should be valid")
                    String email);

    boolean existsByPhoneNumber(@NotBlank(message = "Phone number is required") String phoneNumber);

    User saveUser(User user);

    User getUserById(UUID id);

    Object getAllUsers();

    /**
     * Updates a user's profile. Only an admin may change {@code email} or {@code emailVerified}:
     * the owner-admin rule trusts a verified email, so letting users set their own would let them
     * claim the owner's address and become admin.
     */
    Object updateUser(UUID id, @Valid UpdateUserRequest updateUserRequest, boolean callerIsAdmin);

    void deleteUser(UUID id);

    void unlockUser(UUID id);
}
