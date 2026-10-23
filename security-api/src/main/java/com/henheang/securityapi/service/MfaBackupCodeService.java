package com.henheang.securityapi.service;

import com.henheang.securityapi.domain.User;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public interface MfaBackupCodeService {

    // Deletes any existing codes for the user and generates a fresh set,
    // returning the plaintext codes for one-time display - only their BCrypt
    // hashes are persisted.
    List<String> regenerate(User user);

    // Checks `code` against the user's unused backup codes; if it matches,
    // marks that code used (one-shot) and returns true.
    boolean verifyAndConsume(User user, String code);

    // Deletes all of the user's codes without generating replacements - used
    // when MFA is disabled entirely.
    void clear(User user);
}
