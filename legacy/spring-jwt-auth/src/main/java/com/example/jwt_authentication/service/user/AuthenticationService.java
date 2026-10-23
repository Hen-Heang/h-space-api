package com.example.jwt_authentication.service.user;


import com.example.jwt_authentication.domain.user.User;
import com.example.jwt_authentication.domain.user.UserRepository;
import com.example.jwt_authentication.domain.role.Role;
import com.example.jwt_authentication.domain.role.RoleEnum;
import com.example.jwt_authentication.domain.role.RoleRepository;
import com.example.jwt_authentication.payload.LoginRequest;
import com.example.jwt_authentication.payload.RegisterRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AuthenticationService {
    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManager authenticationManager;
    private final RoleRepository roleRepository;

    public AuthenticationService(
            UserRepository userRepository,
            AuthenticationManager authenticationManager,
            PasswordEncoder passwordEncoder,
            RoleRepository roleRepository) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleRepository = roleRepository;
    }

    public User signup(RegisterRequest input) {
        Optional<Role> optionalRole = roleRepository.findByName(RoleEnum.BUYER);
        if (optionalRole.isEmpty()) {
            return null;
        }
        User user = new User();
        user.setEmail(input.getEmail());
        user.setPassword(passwordEncoder.encode(input.getPassword()));
        user.setFullName(input.getFullName());
        user.setRole(optionalRole.get());
        return userRepository.save(user);
    }

    public User authenticate(LoginRequest input) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        input.getEmail(),
                        input.getPassword()
                )
        );

        return userRepository.findByEmail(input.getEmail())
                .orElseThrow();
    }
}
