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

    Object updateUser(UUID id, @Valid UpdateUserRequest updateUserRequest);

    void deleteUser(UUID id);

    void unlockUser(UUID id);
}
