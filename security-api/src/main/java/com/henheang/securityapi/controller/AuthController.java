package com.henheang.securityapi.controller;

import com.henheang.apicore.components.common.api.ExitCode;
import com.henheang.securityapi.domain.AuditEventType;
import com.henheang.securityapi.domain.Role;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.exception.AuthException;
import com.henheang.securityapi.payload.*;
import com.henheang.securityapi.security.JwtTokenProvider;
import com.henheang.securityapi.security.UserPrincipal;
import com.henheang.securityapi.service.AccountUnlockService;
import com.henheang.securityapi.service.AuditLogService;
import com.henheang.securityapi.service.AuthService;
import com.henheang.securityapi.service.EmailVerificationService;
import com.henheang.securityapi.service.PasswordResetService;
import com.henheang.securityapi.service.RefreshTokenService;
import com.henheang.securityapi.service.TokenBlacklistService;
import com.henheang.securityapi.service.UserService;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController extends BaseController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenService refreshTokenService;
    private final TokenBlacklistService tokenBlacklistService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final EmailVerificationService emailVerificationService;
    private final AccountUnlockService accountUnlockService;

    @Value("${jwt.expiration}")
    private String jwtExpirationString;

    @PostMapping("/login")
    public Object login(@Valid @RequestBody LoginRequest loginRequest) {
        AuthResponse authResponse = (AuthResponse) authService.login(loginRequest);
        return ok(authResponse);
    }

    @PostMapping("/signup")
    public Object signup(@Valid @RequestBody SignUpRequest signUpRequest) {
        AuthResponse authResponse = (AuthResponse) authService.signup(signUpRequest);
        return ok(authResponse);
    }

    // Frontend gets the ID token via Google's own Sign-In SDK and hands it
    // here - the backend only ever verifies it, it never handles a redirect.
    @PostMapping("/oauth2/google")
    public Object loginWithGoogle(@Valid @RequestBody GoogleLoginRequest request) {
        return ok(authService.loginWithGoogle(request.getIdToken()));
    }

    // Lets a sign-up form check a username as it's typed. Answers false for a
    // malformed username too, so the form needs only one rule to show.
    @GetMapping("/username-available")
    public Object usernameAvailable(@RequestParam("username") String username) {
        return ok(java.util.Map.of("available", authService.isUsernameAvailable(username)));
    }

    // "Continue with email", step 1. Always answers OK - whether the address
    // has an account, or a code was already sent moments ago, isn't revealed.
    @PostMapping("/email-code/request")
    public Object requestEmailCode(@Valid @RequestBody EmailCodeRequest request) {
        authService.sendEmailLoginCode(request.getEmail());
        return ok();
    }

    // Step 2: tokens (or an MFA challenge), same shape as /login.
    @PostMapping("/email-code/verify")
    public Object verifyEmailCode(@Valid @RequestBody EmailCodeVerifyRequest request) {
        return ok(authService.loginWithEmailCode(request.getEmail(), request.getCode()));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        // Rotation spends the presented token and issues a new one, so a
        // leaked refresh token can only be used once - even by two requests
        // racing with it.
        return refreshTokenService
                .rotateRefreshToken(request.getRefreshToken())
                .map(
                        rotatedToken -> {
                            User user = rotatedToken.getUser();
                            String accessToken = jwtTokenProvider.generateToken(user);

                            // expiresIn reflects the access token lifetime
                            Duration duration = Duration.parse(jwtExpirationString);
                            long expiresInSeconds = duration.getSeconds();
                            AuthResponse authResponse =
                                    new AuthResponse(
                                            accessToken,
                                            rotatedToken.getRawToken(),
                                            expiresInSeconds);
                            return ok(authResponse);
                        })
                .orElseThrow(
                        () ->
                                new AuthException(
                                        ExitCode.TOKEN_EXPIRED,
                                        "Refresh token is expired. Please login again."));
    }

    @PostMapping("/logout")
    public Object logout(
            @Valid @RequestBody RefreshTokenRequest logoutRequest,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {
        refreshTokenService.logout(logoutRequest.getRefreshToken());

        // Refresh-token revocation alone doesn't stop the still-valid access
        // token from working until it naturally expires, so blacklist it too.
        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
            String accessToken = authorizationHeader.substring(7);
            if (jwtTokenProvider.validateToken(accessToken)) {
                tokenBlacklistService.revoke(
                        jwtTokenProvider.getJtiFromToken(accessToken),
                        jwtTokenProvider.getExpirationFromToken(accessToken));
                auditLogService.log(
                        AuditEventType.LOGOUT,
                        jwtTokenProvider.getUserIdFromToken(accessToken),
                        null);
            }
        }
        return ok();
    }

    @GetMapping("/user")
    public Object getCurrentUser(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        User user = userService.getUserById(userPrincipal.getId());
        UserResponse userResponse =
                new UserResponse(
                        user.getId(),
                        user.getName(),
                        user.getUsername(),
                        user.getEmail(),
                        user.getPhoneNumber(),
                        user.getEmailVerified(),
                        user.getImageUrl(),
                        user.getProvider() != null ? user.getProvider().toString() : "LOCAL");
        userResponse.setRoles(user.getRoles().stream().map(Role::getName).sorted().toList());
        return ok(userResponse);
    }

    //    Forgot password
    @PostMapping("/forgot-password")
    public Object forgotPassword(@Valid @RequestBody PasswordResetRequest passwordResetRequest) {
        passwordResetService.createPasswordResetTokenEmail(passwordResetRequest.getEmail());
        return ok();
    }

    @GetMapping("/reset-password")
    public Object resetPassword(@RequestParam("token") String token) {
        boolean isValidToken = passwordResetService.validatePasswordResetToken(token);
        return ok(new PasswordResetResponse(isValidToken));
    }

    @PostMapping("/reset-password")
    public Object resetPassword(@Valid @RequestBody NewPasswordRequest resetPasswordRequest) {
        passwordResetService.resetPassword(
                resetPasswordRequest.getToken(), resetPasswordRequest.getNewPassword());
        return ok();
    }

    //    Email verification
    @PostMapping("/verify-email")
    public Object verifyEmail(@Valid @RequestBody VerifyEmailRequest verifyEmailRequest) {
        emailVerificationService.verifyEmail(verifyEmailRequest.getToken());
        return ok();
    }

    @PostMapping("/resend-verification")
    public Object resendVerification(
            @Valid @RequestBody ResendVerificationRequest resendVerificationRequest) {
        emailVerificationService.resendVerificationEmail(resendVerificationRequest.getEmail());
        return ok();
    }

    //    Account unlock
    @PostMapping("/unlock-account")
    public Object unlockAccount(@Valid @RequestBody UnlockAccountRequest unlockAccountRequest) {
        accountUnlockService.unlockAccount(unlockAccountRequest.getToken());
        return ok();
    }

    @PostMapping("/resend-unlock-link")
    public Object resendUnlockLink(@Valid @RequestBody ResendUnlockRequest resendUnlockRequest) {
        accountUnlockService.resendUnlockEmail(resendUnlockRequest.getEmail());
        return ok();
    }
}
