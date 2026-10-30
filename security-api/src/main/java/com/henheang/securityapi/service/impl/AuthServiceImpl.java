package com.henheang.securityapi.service.impl;

import com.henheang.apicore.components.common.api.ExitCode;
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
import com.henheang.securityapi.service.AccountAccessService;
import com.henheang.securityapi.service.AccountUnlockService;
import com.henheang.securityapi.service.AuditLogService;
import com.henheang.securityapi.service.AuthService;
import com.henheang.securityapi.service.EmailLoginCodeService;
import com.henheang.securityapi.service.EmailVerificationService;
import com.henheang.securityapi.service.LoginHistoryService;
import com.henheang.securityapi.service.MfaBackupCodeService;
import com.henheang.securityapi.service.MfaService;
import com.henheang.securityapi.service.RefreshTokenService;
import com.henheang.securityapi.service.RoleService;
import com.henheang.securityapi.service.TokenBlacklistService;
import com.henheang.securityapi.service.UserRoleService;
import com.henheang.securityapi.service.UserService;
import com.henheang.securityapi.validation.UsernameRules;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.OptionalLong;
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
    private final EmailLoginCodeService emailLoginCodeService;
    private final AccountAccessService accountAccessService;
    private final TokenBlacklistService tokenBlacklistService;

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
        String username = UsernameRules.normalize(signUpRequest.getUsername());
        if (userRepository.existsByUsername(username)) {
            throw new AuthException(ExitCode.USERNAME_ALREADY_EXISTS);
        }
        accountAccessService.assertMayCreateAccount(signUpRequest.getEmail());

        // Create user
        User user = new User();
        user.setName(signUpRequest.getName());
        user.setUsername(username);
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
            accountAccessService.recordAccountCreated(savedUser);
            if (savedUser.getEmail() != null) {
                emailVerificationService.sendVerificationEmail(savedUser);
            }
            auditLogService.log(AuditEventType.SIGNUP, savedUser.getId(), savedUser.getEmail());
            // No tokens: like login(), a session has to wait for the verified
            // email. Signing in afterwards is what issues them.
            return AuthResponse.emailVerificationRequired();

        } catch (Exception e) {
            logger.error(
                    "Registration failed for {}: {}", signUpRequest.getEmail(), e.getMessage(), e);
            throw new AuthException(
                    ExitCode.REGISTRATION_FAILED, "Registration failed. Please try again.");
        }
    }

    @Override
    public Object login(LoginRequest loginRequest) {
        // Email or username - CustomUserDetailsService resolves it the same way.
        String identifier = loginRequest.getIdentifier().trim();
        try {
            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    identifier, loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Find the user
            User user =
                    findByLoginIdentifier(identifier)
                            .orElseThrow(() -> new AuthException(ExitCode.INVALID_CREDENTIALS));

            // Password verified, but MFA is enabled: don't hand out real tokens
            // yet. Issue a short-lived challenge token that only /mfa/verify
            // will accept, and require the caller to present a valid TOTP code.
            // The failed-attempt count stays until that code is right, so the
            // password alone can't keep resetting it between code guesses.
            if (user.isMfaEnabled()) {
                String mfaToken =
                        jwtTokenProvider.generateToken(
                                user.getId(), "mfa_challenge", MFA_CHALLENGE_DURATION);
                return AuthResponse.mfaRequired(mfaToken);
            }

            clearFailedLoginAttempts(user);
            auditLogService.log(AuditEventType.LOGIN_SUCCESS, user.getId(), user.getEmail());
            loginHistoryService.recordSuccess(user, user.getEmail());
            return issueTokens(user);
        } catch (LockedException e) {
            logger.warn("Login rejected for {}: account locked", identifier);
            auditLogService.log(AuditEventType.ACCOUNT_LOCKED, null, identifier);
            loginHistoryService.recordFailure(identifier, "ACCOUNT_LOCKED");
            throw new AuthException(
                    ExitCode.ACCOUNT_LOCKED,
                    "Account is temporarily locked due to too many failed login attempts. Try again later.");
        } catch (DisabledException e) {
            logger.warn("Login rejected for {}: email not verified", identifier);
            loginHistoryService.recordFailure(identifier, "EMAIL_NOT_VERIFIED");
            throw new AuthException(
                    ExitCode.ACCOUNT_DISABLED,
                    "Please verify your email address before logging in.");
        } catch (AuthenticationException e) {
            logger.warn("Login failed for {}: {}", identifier, e.getMessage());
            findByLoginIdentifier(identifier).ifPresent(this::registerFailedLoginAttempt);
            auditLogService.log(AuditEventType.LOGIN_FAILURE, null, identifier);
            loginHistoryService.recordFailure(identifier, "INVALID_CREDENTIALS");
            throw new AuthException(
                    ExitCode.INVALID_CREDENTIALS, "Invalid email, username or password");
        }
    }

    // Same resolution as CustomUserDetailsService.loadUserByUsername.
    private Optional<User> findByLoginIdentifier(String identifier) {
        return UsernameRules.isEmailIdentifier(identifier)
                ? userRepository.findByEmail(identifier)
                : userRepository.findByUsername(UsernameRules.normalize(identifier));
    }

    @Override
    public boolean isUsernameAvailable(String username) {
        return UsernameRules.isValid(username)
                && !userRepository.existsByUsername(UsernameRules.normalize(username));
    }

    @Override
    public void sendEmailLoginCode(String email) {
        // Invite-only: an address that has no account and can't create one
        // gets no code. The response is the same either way (the controller
        // always answers OK), so this reveals nothing about the address.
        boolean hasAccount =
                userRepository
                        .findFirstByEmailIgnoreCaseOrderByCreatedAtAsc(
                                EmailLoginCodeService.normalizeEmail(email))
                        .isPresent();
        if (!hasAccount && !accountAccessService.mayCreateAccount(email)) {
            logger.info("No sign-in code sent to {}: no account and no invite", email);
            return;
        }
        emailLoginCodeService.sendCode(email);
    }

    @Override
    @Transactional
    public Object loginWithEmailCode(String rawEmail, String code) {
        String email = EmailLoginCodeService.normalizeEmail(rawEmail);
        if (!emailLoginCodeService.consumeCode(email, code)) {
            auditLogService.log(AuditEventType.LOGIN_FAILURE, null, email, "via email code");
            loginHistoryService.recordFailure(email, "EMAIL_CODE_INVALID");
            throw new AuthException(ExitCode.EMAIL_LOGIN_CODE_INVALID);
        }

        // The code proves the caller controls this mailbox, which is all an
        // account needs: an unknown address becomes a new account, and an
        // unverified one is verified by it.
        User user =
                userRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtAsc(email).orElse(null);
        if (user == null) {
            accountAccessService.assertMayCreateAccount(email);
            user = new User();
            user.setEmail(email);
            user.setProvider(AuthProvider.LOCAL);
            user.setEmailVerified(true);
            user = userService.saveUser(user);
            userRoleService.assignDefaultRole(user, roleService.getOrCreateRole("ROLE_USER"));
            accountAccessService.recordAccountCreated(user);
            auditLogService.log(AuditEventType.SIGNUP, user.getId(), email, "via email code");
        } else {
            if (!user.isAccountNonLocked()) {
                loginHistoryService.recordFailure(email, "ACCOUNT_LOCKED");
                throw new AuthException(
                        ExitCode.ACCOUNT_LOCKED,
                        "Account is temporarily locked due to too many failed login attempts. Try again later.");
            }
            if (!Boolean.TRUE.equals(user.getEmailVerified())) {
                user.setEmailVerified(true);
                user = userRepository.save(user);
                auditLogService.log(
                        AuditEventType.EMAIL_VERIFIED, user.getId(), email, "via email code");
            }
        }

        // An email code replaces the password, not the second factor.
        if (user.isMfaEnabled()) {
            String mfaToken =
                    jwtTokenProvider.generateToken(
                            user.getId(), "mfa_challenge", MFA_CHALLENGE_DURATION);
            return AuthResponse.mfaRequired(mfaToken);
        }

        auditLogService.log(
                AuditEventType.LOGIN_SUCCESS, user.getId(), user.getEmail(), "via email code");
        loginHistoryService.recordSuccess(user, user.getEmail());
        return issueTokens(user);
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

    private void clearFailedLoginAttempts(User user) {
        if (user.getFailedLoginAttempts() > 0 || user.getLockedUntil() != null) {
            user.setFailedLoginAttempts(0);
            user.setLockedUntil(null);
            userRepository.save(user);
        }
    }

    @Override
    public Object verifyMfaAndIssueTokens(String mfaToken, String code) {
        if (!jwtTokenProvider.validateToken(mfaToken)
                || !"mfa_challenge".equals(jwtTokenProvider.getTokenTypeFromToken(mfaToken))
                || tokenBlacklistService.isRevoked(jwtTokenProvider.getJtiFromToken(mfaToken))) {
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

        // Wrong codes count toward the same lock as wrong passwords.
        if (!user.isAccountNonLocked()) {
            loginHistoryService.recordFailure(user.getEmail(), "ACCOUNT_LOCKED");
            throw new AuthException(
                    ExitCode.ACCOUNT_LOCKED,
                    "Account is temporarily locked due to too many failed login attempts. Try again later.");
        }

        // A code is accepted once: its time step must be later than the last
        // one used. A replayed code falls through and counts as a wrong one.
        OptionalLong step = mfaService.matchTimeStep(user.getMfaSecret(), code);
        if (step.isPresent()
                && userRepository.claimMfaTimeStep(user.getId(), step.getAsLong()) == 1) {
            user.setMfaLastUsedStep(step.getAsLong());
            auditLogService.log(
                    AuditEventType.LOGIN_SUCCESS, user.getId(), user.getEmail(), "via MFA");
            return completeMfaSignIn(user, mfaToken);
        }

        if (mfaBackupCodeService.verifyAndConsume(user, code)) {
            auditLogService.log(AuditEventType.MFA_BACKUP_CODE_USED, user.getId(), user.getEmail());
            auditLogService.log(
                    AuditEventType.LOGIN_SUCCESS,
                    user.getId(),
                    user.getEmail(),
                    "via MFA backup code");
            return completeMfaSignIn(user, mfaToken);
        }

        registerFailedLoginAttempt(user);
        auditLogService.log(AuditEventType.MFA_CHALLENGE_FAILED, user.getId(), user.getEmail());
        throw new AuthException(ExitCode.MFA_CODE_INVALID);
    }

    // A challenge signs in once: it's revoked before the tokens are issued.
    private AuthResponse completeMfaSignIn(User user, String mfaToken) {
        tokenBlacklistService.revoke(
                jwtTokenProvider.getJtiFromToken(mfaToken),
                jwtTokenProvider.getExpirationFromToken(mfaToken));
        clearFailedLoginAttempts(user);
        return issueTokens(user);
    }

    @Override
    @Transactional
    public Object loginWithGoogle(String idToken) {
        GoogleTokenVerifier.GooglePrincipal googlePrincipal = googleTokenVerifier.verify(idToken);

        User user = userRepository.findByEmail(googlePrincipal.email()).orElse(null);

        if (user == null) {
            accountAccessService.assertMayCreateAccount(googlePrincipal.email());
            user = new User();
            user.setEmail(googlePrincipal.email());
            user.setName(googlePrincipal.name());
            user.setProvider(AuthProvider.GOOGLE);
            user.setProviderId(googlePrincipal.subject());
            // Google already verified this address - no local password exists to check.
            user.setEmailVerified(true);
            user = userService.saveUser(user);
            userRoleService.assignDefaultRole(user, roleService.getOrCreateRole("ROLE_USER"));
            accountAccessService.recordAccountCreated(user);
            auditLogService.log(AuditEventType.SIGNUP, user.getId(), user.getEmail(), "via Google");
        } else if (user.getProvider() != AuthProvider.GOOGLE) {
            // Don't let a Google sign-in silently take over an account that was
            // created (and is protected) by a local password.
            throw new AuthException(
                    ExitCode.OAUTH_ERROR,
                    "This email is already registered with a different sign-in method");
        } else if (!user.isAccountNonLocked()) {
            loginHistoryService.recordFailure(user.getEmail(), "ACCOUNT_LOCKED");
            throw new AuthException(
                    ExitCode.ACCOUNT_LOCKED,
                    "Account is temporarily locked due to too many failed login attempts. Try again later.");
        }

        // Google replaces the password, not the second factor.
        if (user.isMfaEnabled()) {
            String mfaToken =
                    jwtTokenProvider.generateToken(
                            user.getId(), "mfa_challenge", MFA_CHALLENGE_DURATION);
            return AuthResponse.mfaRequired(mfaToken);
        }

        auditLogService.log(
                AuditEventType.LOGIN_SUCCESS, user.getId(), user.getEmail(), "via Google");
        return issueTokens(user);
    }

    // Every completed sign-in ends here (password, MFA, email code, Google),
    // so this is where the owner's account picks up ROLE_ADMIN.
    private AuthResponse issueTokens(User user) {
        accountAccessService.ensureOwnerIsAdmin(user);
        String accessToken = jwtTokenProvider.generateToken(user);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        // expiresIn reflects the access token's lifetime, not the refresh token's
        Duration duration = Duration.parse(jwtExpirationString);
        long expiresInSeconds = duration.getSeconds();

        return new AuthResponse(accessToken, refreshToken.getRawToken(), expiresInSeconds);
    }
}
