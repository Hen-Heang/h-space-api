package com.henheang.securityapi.service.impl;

import com.henheang.commonapi.components.common.api.ExitCode;
import com.henheang.securityapi.domain.AuditEventType;
import com.henheang.securityapi.domain.AuthProvider;
import com.henheang.securityapi.domain.RefreshToken;
import com.henheang.securityapi.domain.Role;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.exception.AuthException;
import com.henheang.securityapi.payload.AuthResponse;
import com.henheang.securityapi.payload.LoginRequest;
import com.henheang.securityapi.payload.SignUpRequest;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.security.JwtTokenProvider;
import com.henheang.securityapi.security.oauth.GoogleTokenVerifier;
import com.henheang.securityapi.service.AccountUnlockService;
import com.henheang.securityapi.service.AuditLogService;
import com.henheang.securityapi.service.AuthService;
import com.henheang.securityapi.service.EmailVerificationService;
import com.henheang.securityapi.service.LoginHistoryService;
import com.henheang.securityapi.service.MfaBackupCodeService;
import com.henheang.securityapi.service.MfaService;
import com.henheang.securityapi.service.RefreshTokenService;
import com.henheang.securityapi.service.RoleService;
import com.henheang.securityapi.service.UserRoleService;
import com.henheang.securityapi.service.UserService;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final Duration MFA_CHALLENGE_DURATION = Duration.ofMinutes(5);

    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final RoleService roleService;
    private final UserRoleService userRoleService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final MfaService mfaService;
    private final MfaBackupCodeService mfaBackupCodeService;
    private final AuditLogService auditLogService;
    private final LoginHistoryService loginHistoryService;
    private final GoogleTokenVerifier googleTokenVerifier;
    private final EmailVerificationService emailVerificationService;
    private final AccountUnlockService accountUnlockService;

    @Value("${jwt.expiration}")
    private String jwtExpirationString;

    @Value("${app.account-lock.max-failed-attempts:5}")
    private int maxFailedLoginAttempts;

    @Value("${app.account-lock.lock-duration-minutes:15}")
    private long accountLockDurationMinutes;

    @Override
    @Transactional
    public Object signup(SignUpRequest signUpRequest) {
        // Check if email already exists
        if (userService.existsByEmail(signUpRequest.getEmail())) {
            throw new AuthException(ExitCode.EMAIL_ALREADY_EXISTS);
        }

        // Create user
        User user = new User();
        user.setName(signUpRequest.getName());
        user.setEmail(signUpRequest.getEmail());
        user.setPassword(passwordEncoder.encode(signUpRequest.getPassword()));
        user.setProvider(AuthProvider.LOCAL);
        // Phone-only signups have no address to verify, so they stay usable
        // immediately; email signups must confirm ownership before login()
        // will let them in (see the DisabledException catch below).
        user.setEmailVerified(user.getEmail() == null);

        try {
            User savedUser = userService.saveUser(user);
            // UserRole rows need the user's id, so the default role is
            // assigned after the initial save rather than cascaded with it.
            Role role = roleService.getOrCreateRole("ROLE_USER");
            userRoleService.assignDefaultRole(savedUser, role);
            if (savedUser.getEmail() != null) {
                emailVerificationService.sendVerificationEmail(savedUser);
            }
            // Generate JWT token
            String token = jwtTokenProvider.generateToken(savedUser);
            // Generate refresh token
            RefreshToken refreshToken = refreshTokenService.createRefreshToken(savedUser);
            // expiresIn reflects the access token's lifetime, not the refresh token's
            Duration duration = Duration.parse(jwtExpirationString);

            Long expirationTimeInSeconds = duration.getSeconds();
            auditLogService.log(AuditEventType.SIGNUP, savedUser.getId(), savedUser.getEmail());
            return new AuthResponse(token, refreshToken.getRawToken(), expirationTimeInSeconds);

        } catch (Exception e) {
            logger.error(
                    "Registration failed for {}: {}", signUpRequest.getEmail(), e.getMessage(), e);
            throw new AuthException(
                    ExitCode.REGISTRATION_FAILED, "Registration failed. Please try again.");
        }
    }

    @Override
    public Object login(LoginRequest loginRequest) {
        try {
            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    loginRequest.getEmail(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Find the user
            User user =
                    userRepository
                            .findByEmail(loginRequest.getEmail())
                            .orElseThrow(() -> new AuthException(ExitCode.INVALID_CREDENTIALS));

            // Successful login: clear any accumulated failed-attempt count/lock
            if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
                userRepository.save(user);
            }

            // Password verified, but MFA is enabled: don't hand out real tokens
            // yet. Issue a short-lived challenge token that only /mfa/verify
            // will accept, and require the caller to present a valid TOTP code.
            if (user.isMfaEnabled()) {
                String mfaToken =
                        jwtTokenProvider.generateToken(
                                user.getId(), "mfa_challenge", MFA_CHALLENGE_DURATION);
                return AuthResponse.mfaRequired(mfaToken);
            }

            auditLogService.log(AuditEventType.LOGIN_SUCCESS, user.getId(), user.getEmail());
            loginHistoryService.recordSuccess(user, user.getEmail());
            return issueTokens(user);
        } catch (LockedException e) {
            logger.warn("Login rejected for {}: account locked", loginRequest.getEmail());
            auditLogService.log(AuditEventType.ACCOUNT_LOCKED, null, loginRequest.getEmail());
            loginHistoryService.recordFailure(loginRequest.getEmail(), "ACCOUNT_LOCKED");
            throw new AuthException(
                    ExitCode.ACCOUNT_LOCKED,
                    "Account is temporarily locked due to too many failed login attempts. Try again later.");
        } catch (DisabledException e) {
            logger.warn("Login rejected for {}: email not verified", loginRequest.getEmail());
            loginHistoryService.recordFailure(loginRequest.getEmail(), "EMAIL_NOT_VERIFIED");
            throw new AuthException(
                    ExitCode.ACCOUNT_DISABLED,
                    "Please verify your email address before logging in.");
        } catch (AuthenticationException e) {
            logger.warn("Login failed for {}: {}", loginRequest.getEmail(), e.getMessage());
            userRepository
                    .findByEmail(loginRequest.getEmail())
                    .ifPresent(this::registerFailedLoginAttempt);
            auditLogService.log(AuditEventType.LOGIN_FAILURE, null, loginRequest.getEmail());
            loginHistoryService.recordFailure(loginRequest.getEmail(), "INVALID_CREDENTIALS");
            throw new AuthException(ExitCode.INVALID_CREDENTIALS, "Invalid email or password");
        }
    }

    private void registerFailedLoginAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        boolean justLocked = false;
        if (attempts >= maxFailedLoginAttempts) {
            user.setLockedUntil(Instant.now().plus(Duration.ofMinutes(accountLockDurationMinutes)));
            justLocked = true;
        }
        userRepository.save(user);
        if (justLocked && user.getEmail() != null) {
            accountUnlockService.sendUnlockEmail(user);
        }
    }

    @Override
    public Object verifyMfaAndIssueTokens(String mfaToken, String code) {
        if (!jwtTokenProvider.validateToken(mfaToken)
                || !"mfa_challenge".equals(jwtTokenProvider.getTokenTypeFromToken(mfaToken))) {
            throw new AuthException(
                    ExitCode.TOKEN_INVALID, "Invalid or expired MFA challenge token");
        }

        UUID userId = jwtTokenProvider.getUserIdFromToken(mfaToken);
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new AuthException(ExitCode.INVALID_CREDENTIALS));

        if (!user.isMfaEnabled()) {
            auditLogService.log(AuditEventType.MFA_CHALLENGE_FAILED, user.getId(), user.getEmail());
            throw new AuthException(ExitCode.MFA_CODE_INVALID);
        }

        if (mfaService.verifyCode(user.getMfaSecret(), code)) {
            auditLogService.log(
                    AuditEventType.LOGIN_SUCCESS, user.getId(), user.getEmail(), "via MFA");
            return issueTokens(user);
        }

        if (mfaBackupCodeService.verifyAndConsume(user, code)) {
            auditLogService.log(AuditEventType.MFA_BACKUP_CODE_USED, user.getId(), user.getEmail());
            auditLogService.log(
                    AuditEventType.LOGIN_SUCCESS,
                    user.getId(),
                    user.getEmail(),
                    "via MFA backup code");
            return issueTokens(user);
        }

        auditLogService.log(AuditEventType.MFA_CHALLENGE_FAILED, user.getId(), user.getEmail());
        throw new AuthException(ExitCode.MFA_CODE_INVALID);
    }

    @Override
    @Transactional
    public Object loginWithGoogle(String idToken) {
        GoogleTokenVerifier.GooglePrincipal googlePrincipal = googleTokenVerifier.verify(idToken);

        User user = userRepository.findByEmail(googlePrincipal.email()).orElse(null);

        if (user == null) {
            user = new User();
            user.setEmail(googlePrincipal.email());
            user.setName(googlePrincipal.name());
            user.setProvider(AuthProvider.GOOGLE);
            user.setProviderId(googlePrincipal.subject());
            // Google already verified this address - no local password exists to check.
            user.setEmailVerified(true);
            user = userService.saveUser(user);
            userRoleService.assignDefaultRole(user, roleService.getOrCreateRole("ROLE_USER"));
            auditLogService.log(AuditEventType.SIGNUP, user.getId(), user.getEmail(), "via Google");
        } else if (user.getProvider() != AuthProvider.GOOGLE) {
            // Don't let a Google sign-in silently take over an account that was
            // created (and is protected) by a local password.
            throw new AuthException(
                    ExitCode.OAUTH_ERROR,
                    "This email is already registered with a different sign-in method");
        }

        auditLogService.log(
                AuditEventType.LOGIN_SUCCESS, user.getId(), user.getEmail(), "via Google");
        return issueTokens(user);
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtTokenProvider.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        // expiresIn reflects the access token's lifetime, not the refresh token's
        Duration duration = Duration.parse(jwtExpirationString);
        long expiresInSeconds = duration.getSeconds();

        return new AuthResponse(accessToken, refreshToken.getRawToken(), expiresInSeconds);
    }
}
