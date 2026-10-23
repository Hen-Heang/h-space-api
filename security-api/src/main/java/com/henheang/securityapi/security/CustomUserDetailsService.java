package com.henheang.securityapi.security;

import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.exception.ResourceNotFoundException;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.utils.PhoneNumberUtil;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(
                                () ->
                                        new UsernameNotFoundException(
                                                "User not found with email : " + email));

        return UserPrincipal.create(user);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(UUID id) {
        User user =
                userRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));

        return UserPrincipal.create(user);
    }

    /*
    Find the user by email or phone number.
     */

    @Transactional(readOnly = true)
    public java.util.Optional<User> findUserByIdentifier(String identifier) {
        if (identifier == null || identifier.trim().isEmpty()) {
            return java.util.Optional.empty();
        }

        String cleanIdentifier = identifier.trim();

        // First try to find by exact match (email or phone)
        java.util.Optional<User> user = userRepository.findByEmailOrPhoneNumber(cleanIdentifier);

        if (user.isPresent()) {
            return user;
        }

        // If it looks like a phone number, try normalizing it
        if (PhoneNumberUtil.isPhoneNumber(cleanIdentifier)) {
            String normalizedPhone = PhoneNumberUtil.normalizePhoneNumber(cleanIdentifier);
            if (!normalizedPhone.equals(cleanIdentifier)) {
                return userRepository.findByPhoneNumber(normalizedPhone);
            }
        }

        return java.util.Optional.empty();
    }
}
