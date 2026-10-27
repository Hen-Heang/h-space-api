package com.henheang.securityapi.service;

import com.henheang.securityapi.payload.LoginRequest;
import com.henheang.securityapi.payload.SignUpRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;

@Service
public interface AuthService {

    Object signup(@Valid SignUpRequest signUpRequest);

    /** Password sign-in; the request's identifier may be an email or a username. */
    Object login(@Valid LoginRequest loginRequest);

    Object verifyMfaAndIssueTokens(String mfaToken, String code);

    Object loginWithGoogle(String idToken);

    /** False for a malformed username as well as a taken one. */
    boolean isUsernameAvailable(String username);

    /** First step of "continue with email": sends a 6-digit code. */
    void sendEmailLoginCode(String email);

    /**
     * Second step: a correct code signs in, creating the account first if the address has none.
     * Returns an MFA challenge instead of tokens when the account has MFA enabled.
     */
    Object loginWithEmailCode(String email, String code);
}
