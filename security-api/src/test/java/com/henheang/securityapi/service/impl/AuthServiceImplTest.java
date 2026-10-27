package com.henheang.securityapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.henheang.apicore.components.common.api.ExitCode;
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
import com.henheang.securityapi.service.EmailLoginCodeService;
import com.henheang.securityapi.service.EmailVerificationService;
import com.henheang.securityapi.service.LoginHistoryService;
import com.henheang.securityapi.service.MfaBackupCodeService;
import com.henheang.securityapi.service.MfaService;
import com.henheang.securityapi.service.RefreshTokenService;
import com.henheang.securityapi.service.RoleService;
import com.henheang.securityapi.service.UserRoleService;
import com.henheang.securityapi.service.UserService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class AuthServiceImplTest {

    private AuthenticationManager authenticationManager;
    private UserService userService;
    private RoleService roleService;
    private UserRoleService userRoleService;
    private PasswordEncoder passwordEncoder;
    private JwtTokenProvider jwtTokenProvider;
    private UserRepository userRepository;
    private RefreshTokenService refreshTokenService;
    private MfaService mfaService;
    private MfaBackupCodeService mfaBackupCodeService;
    private AuditLogService auditLogService;
    private LoginHistoryService loginHistoryService;
    private GoogleTokenVerifier googleTokenVerifier;
    private EmailVerificationService emailVerificationService;
    private AccountUnlockService accountUnlockService;
    private EmailLoginCodeService emailLoginCodeService;
    private AccountAccessService accountAccessService;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        authenticationManager = mock(AuthenticationManager.class);
        userService = mock(UserService.class);
        roleService = mock(RoleService.class);
        userRoleService = mock(UserRoleService.class);
        passwordEncoder = mock(PasswordEncoder.class);
        jwtTokenProvider = mock(JwtTokenProvider.class);
        userRepository = mock(UserRepository.class);
        refreshTokenService = mock(RefreshTokenService.class);
        mfaService = mock(MfaService.class);
        mfaBackupCodeService = mock(MfaBackupCodeService.class);
        auditLogService = mock(AuditLogService.class);
        loginHistoryService = mock(LoginHistoryService.class);
        googleTokenVerifier = mock(GoogleTokenVerifier.class);
        emailVerificationService = mock(EmailVerificationService.class);
        accountUnlockService = mock(AccountUnlockService.class);
        emailLoginCodeService = mock(EmailLoginCodeService.class);
        accountAccessService = mock(AccountAccessService.class);

        authService =
                new AuthServiceImpl(
                        authenticationManager,
                        userService,
                        roleService,
                        userRoleService,
                        passwordEncoder,
                        jwtTokenProvider,
                        userRepository,
                        refreshTokenService,
                        mfaService,
                        mfaBackupCodeService,
                        auditLogService,
                        loginHistoryService,
                        googleTokenVerifier,
                        emailVerificationService,
                        accountUnlockService,
                        emailLoginCodeService,
                        accountAccessService);
        ReflectionTestUtils.setField(authService, "jwtExpirationString", "PT24H");
        ReflectionTestUtils.setField(authService, "maxFailedLoginAttempts", 5);
        ReflectionTestUtils.setField(authService, "accountLockDurationMinutes", 15L);
    }

    private User persistedUser(UUID id) {
        User user = new User();
        user.setId(id);
        user.setEmail("user@example.com");
        return user;
    }

    private com.henheang.securityapi.domain.RefreshToken refreshTokenFor(User user) {
        com.henheang.securityapi.domain.RefreshToken token =
                new com.henheang.securityapi.domain.RefreshToken();
        token.setUser(user);
        token.setRawToken("raw-refresh-token");
        return token;
    }

    @Test
    void signup_createsUserAndIssuesTokens() {
        SignUpRequest request =
                new SignUpRequest("Jane Doe", "jane@example.com", null, "password123", "Jane.Doe");
        when(userService.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed-password");
        when(roleService.getOrCreateRole("ROLE_USER")).thenReturn(new Role());
        UUID userId = UUID.randomUUID();
        User saved = persistedUser(userId);
        when(userService.saveUser(any(User.class))).thenReturn(saved);
        when(jwtTokenProvider.generateToken(saved)).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(saved)).thenReturn(refreshTokenFor(saved));

        Object result = authService.signup(request);

        assertThat(result).isInstanceOf(AuthResponse.class);
        AuthResponse response = (AuthResponse) result;
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("raw-refresh-token");
        verify(auditLogService)
                .log(
                        com.henheang.securityapi.domain.AuditEventType.SIGNUP,
                        userId,
                        "user@example.com");
    }

    @Test
    void signup_withExistingEmail_throwsAuthException() {
        SignUpRequest request =
                new SignUpRequest("Jane Doe", "jane@example.com", null, "password123", "Jane.Doe");
        when(userService.existsByEmail(request.getEmail())).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(AuthException.class)
                .satisfies(
                        ex ->
                                assertThat(((AuthException) ex).getExitCode())
                                        .isEqualTo(ExitCode.EMAIL_ALREADY_EXISTS));

        verify(userService, never()).saveUser(any());
    }

    @Test
    void login_withoutMfa_issuesTokens() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        UUID userId = UUID.randomUUID();
        User user = persistedUser(userId);
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken(user)).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn(refreshTokenFor(user));

        Object result = authService.login(request);

        assertThat(result).isInstanceOf(AuthResponse.class);
        AuthResponse response = (AuthResponse) result;
        assertThat(response.isMfaRequired()).isFalse();
        assertThat(response.getAccessToken()).isEqualTo("access-token");
        verify(auditLogService)
                .log(
                        com.henheang.securityapi.domain.AuditEventType.LOGIN_SUCCESS,
                        userId,
                        "user@example.com");
        verify(loginHistoryService).recordSuccess(user, "user@example.com");
    }

    @Test
    void login_withMfaEnabled_returnsChallengeInsteadOfTokens() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        UUID userId = UUID.randomUUID();
        User user = persistedUser(userId);
        user.setMfaEnabled(true);
        Authentication authentication = mock(Authentication.class);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken(eq(userId), eq("mfa_challenge"), any()))
                .thenReturn("mfa-challenge-token");

        Object result = authService.login(request);

        assertThat(result).isInstanceOf(AuthResponse.class);
        AuthResponse response = (AuthResponse) result;
        assertThat(response.isMfaRequired()).isTrue();
        assertThat(response.getMfaToken()).isEqualTo("mfa-challenge-token");
        assertThat(response.getAccessToken()).isNull();
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    @Test
    void login_withBadCredentials_recordsFailedAttemptAndThrows() {
        LoginRequest request = new LoginRequest("user@example.com", "wrong-password");
        User user = persistedUser(UUID.randomUUID());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .satisfies(
                        ex ->
                                assertThat(((AuthException) ex).getExitCode())
                                        .isEqualTo(ExitCode.INVALID_CREDENTIALS));

        assertThat(user.getFailedLoginAttempts()).isEqualTo(1);
        verify(userRepository, times(1)).save(user);
        verify(auditLogService)
                .log(
                        com.henheang.securityapi.domain.AuditEventType.LOGIN_FAILURE,
                        null,
                        "user@example.com");
        verify(loginHistoryService).recordFailure("user@example.com", "INVALID_CREDENTIALS");
    }

    @Test
    void login_whenAccountLocked_throwsAuthException() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new LockedException("account locked"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .satisfies(
                        ex ->
                                assertThat(((AuthException) ex).getExitCode())
                                        .isEqualTo(ExitCode.ACCOUNT_LOCKED));

        verify(auditLogService)
                .log(
                        com.henheang.securityapi.domain.AuditEventType.ACCOUNT_LOCKED,
                        null,
                        "user@example.com");
        verify(loginHistoryService).recordFailure("user@example.com", "ACCOUNT_LOCKED");
    }

    @Test
    void login_whenEmailNotVerified_throwsAuthException() {
        LoginRequest request = new LoginRequest("user@example.com", "password123");
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("account disabled"));

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(AuthException.class)
                .satisfies(
                        ex ->
                                assertThat(((AuthException) ex).getExitCode())
                                        .isEqualTo(ExitCode.ACCOUNT_DISABLED));

        verify(loginHistoryService).recordFailure("user@example.com", "EMAIL_NOT_VERIFIED");
    }

    @Test
    void signup_withEmail_sendsVerificationEmail() {
        SignUpRequest request =
                new SignUpRequest("Jane Doe", "jane@example.com", null, "password123", "Jane.Doe");
        when(userService.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("hashed-password");
        when(roleService.getOrCreateRole("ROLE_USER")).thenReturn(new Role());
        User saved = persistedUser(UUID.randomUUID());
        when(userService.saveUser(any(User.class))).thenReturn(saved);
        when(jwtTokenProvider.generateToken(saved)).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(saved)).thenReturn(refreshTokenFor(saved));

        authService.signup(request);

        verify(emailVerificationService).sendVerificationEmail(saved);
    }

    @Test
    void login_crossingFailureThreshold_locksAccountAndSendsUnlockEmail() {
        LoginRequest request = new LoginRequest("user@example.com", "wrong-password");
        User user = persistedUser(UUID.randomUUID());
        user.setFailedLoginAttempts(4);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(AuthException.class);

        assertThat(user.getFailedLoginAttempts()).isEqualTo(5);
        assertThat(user.getLockedUntil()).isNotNull();
        verify(accountUnlockService).sendUnlockEmail(user);
    }

    @Test
    void signup_storesUsernameLowercase() {
        SignUpRequest request =
                new SignUpRequest("Jane Doe", "jane@example.com", null, "password123", "Jane.Doe");
        when(userService.existsByEmail(request.getEmail())).thenReturn(false);
        when(roleService.getOrCreateRole("ROLE_USER")).thenReturn(new Role());
        User saved = persistedUser(UUID.randomUUID());
        when(userService.saveUser(any(User.class))).thenReturn(saved);
        when(refreshTokenService.createRefreshToken(saved)).thenReturn(refreshTokenFor(saved));

        authService.signup(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).saveUser(captor.capture());
        assertThat(captor.getValue().getUsername()).isEqualTo("jane.doe");
    }

    @Test
    void signup_withTakenUsername_throwsAuthException() {
        SignUpRequest request =
                new SignUpRequest("Jane Doe", "jane@example.com", null, "password123", "Jane.Doe");
        when(userService.existsByEmail(request.getEmail())).thenReturn(false);
        when(userRepository.existsByUsername("jane.doe")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(AuthException.class)
                .satisfies(
                        ex ->
                                assertThat(((AuthException) ex).getExitCode())
                                        .isEqualTo(ExitCode.USERNAME_ALREADY_EXISTS));

        verify(userService, never()).saveUser(any());
    }

    @Test
    void login_withUsername_looksUpByNormalizedUsername() {
        LoginRequest request = new LoginRequest("  Jane.Doe ", "password123");
        User user = persistedUser(UUID.randomUUID());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mock(Authentication.class));
        when(userRepository.findByUsername("jane.doe")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken(user)).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(user)).thenReturn(refreshTokenFor(user));

        AuthResponse response = (AuthResponse) authService.login(request);

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        ArgumentCaptor<UsernamePasswordAuthenticationToken> captor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(captor.capture());
        assertThat(captor.getValue().getPrincipal()).isEqualTo("Jane.Doe");
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void isUsernameAvailable_rejectsMalformedAndTakenNames() {
        when(userRepository.existsByUsername("taken")).thenReturn(true);

        assertThat(authService.isUsernameAvailable("free_name")).isTrue();
        assertThat(authService.isUsernameAvailable("Taken")).isFalse();
        assertThat(authService.isUsernameAvailable("ab")).isFalse();
        assertThat(authService.isUsernameAvailable("has@sign")).isFalse();
        assertThat(authService.isUsernameAvailable("_leading")).isFalse();
    }

    @Test
    void loginWithEmailCode_withWrongCode_throwsAndIssuesNothing() {
        when(emailLoginCodeService.consumeCode("new@example.com", "123456")).thenReturn(false);

        assertThatThrownBy(() -> authService.loginWithEmailCode("New@Example.com ", "123456"))
                .isInstanceOf(AuthException.class)
                .satisfies(
                        ex ->
                                assertThat(((AuthException) ex).getExitCode())
                                        .isEqualTo(ExitCode.EMAIL_LOGIN_CODE_INVALID));

        verify(userService, never()).saveUser(any());
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    @Test
    void loginWithEmailCode_forUnknownEmail_createsVerifiedAccount() {
        when(emailLoginCodeService.consumeCode("new@example.com", "123456")).thenReturn(true);
        when(userRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtAsc("new@example.com"))
                .thenReturn(Optional.empty());
        when(roleService.getOrCreateRole("ROLE_USER")).thenReturn(new Role());
        User saved = persistedUser(UUID.randomUUID());
        when(userService.saveUser(any(User.class))).thenReturn(saved);
        when(jwtTokenProvider.generateToken(saved)).thenReturn("access-token");
        when(refreshTokenService.createRefreshToken(saved)).thenReturn(refreshTokenFor(saved));

        AuthResponse response =
                (AuthResponse) authService.loginWithEmailCode("New@Example.com", "123456");

        assertThat(response.getAccessToken()).isEqualTo("access-token");
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userService).saveUser(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo("new@example.com");
        assertThat(captor.getValue().getEmailVerified()).isTrue();
        assertThat(captor.getValue().getPassword()).isNull();
    }

    @Test
    void loginWithEmailCode_verifiesAnUnverifiedExistingAccount() {
        User user = persistedUser(UUID.randomUUID());
        user.setEmailVerified(false);
        when(emailLoginCodeService.consumeCode("user@example.com", "123456")).thenReturn(true);
        when(userRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtAsc("user@example.com"))
                .thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(refreshTokenService.createRefreshToken(user)).thenReturn(refreshTokenFor(user));

        authService.loginWithEmailCode("user@example.com", "123456");

        assertThat(user.getEmailVerified()).isTrue();
        verify(userService, never()).saveUser(any());
    }

    @Test
    void loginWithEmailCode_withMfaEnabled_returnsChallengeInsteadOfTokens() {
        User user = persistedUser(UUID.randomUUID());
        user.setEmailVerified(true);
        user.setMfaEnabled(true);
        when(emailLoginCodeService.consumeCode("user@example.com", "123456")).thenReturn(true);
        when(userRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtAsc("user@example.com"))
                .thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken(eq(user.getId()), eq("mfa_challenge"), any()))
                .thenReturn("mfa-challenge-token");

        AuthResponse response =
                (AuthResponse) authService.loginWithEmailCode("user@example.com", "123456");

        assertThat(response.isMfaRequired()).isTrue();
        assertThat(response.getAccessToken()).isNull();
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    @Test
    void loginWithEmailCode_forLockedAccount_throws() {
        User user = persistedUser(UUID.randomUUID());
        user.setEmailVerified(true);
        user.setLockedUntil(java.time.Instant.now().plusSeconds(600));
        when(emailLoginCodeService.consumeCode("user@example.com", "123456")).thenReturn(true);
        when(userRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtAsc("user@example.com"))
                .thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.loginWithEmailCode("user@example.com", "123456"))
                .isInstanceOf(AuthException.class)
                .satisfies(
                        ex ->
                                assertThat(((AuthException) ex).getExitCode())
                                        .isEqualTo(ExitCode.ACCOUNT_LOCKED));
        verify(refreshTokenService, never()).createRefreshToken(any());
    }

    private void refuseNewAccounts() {
        AuthException refused = new AuthException(ExitCode.SIGNUP_INVITE_REQUIRED);
        org.mockito.Mockito.doThrow(refused)
                .when(accountAccessService)
                .assertMayCreateAccount(any());
    }

    @Test
    void signup_whenNotInvited_createsNothing() {
        refuseNewAccounts();
        SignUpRequest request =
                new SignUpRequest("Jane Doe", "jane@example.com", null, "Password123!", "jane");

        assertThatThrownBy(() -> authService.signup(request))
                .isInstanceOf(AuthException.class)
                .satisfies(
                        ex ->
                                assertThat(((AuthException) ex).getExitCode())
                                        .isEqualTo(ExitCode.SIGNUP_INVITE_REQUIRED));
        verify(userService, never()).saveUser(any());
    }

    @Test
    void signup_usesUpTheInvite() {
        SignUpRequest request =
                new SignUpRequest("Jane Doe", "jane@example.com", null, "Password123!", "jane");
        when(roleService.getOrCreateRole("ROLE_USER")).thenReturn(new Role());
        User saved = persistedUser(UUID.randomUUID());
        when(userService.saveUser(any(User.class))).thenReturn(saved);
        when(refreshTokenService.createRefreshToken(saved)).thenReturn(refreshTokenFor(saved));

        authService.signup(request);

        verify(accountAccessService).recordAccountCreated(saved);
    }

    @Test
    void loginWithEmailCode_forAnUninvitedNewAddress_createsNothing() {
        refuseNewAccounts();
        when(emailLoginCodeService.consumeCode("new@example.com", "123456")).thenReturn(true);
        when(userRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtAsc("new@example.com"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loginWithEmailCode("new@example.com", "123456"))
                .isInstanceOf(AuthException.class);
        verify(userService, never()).saveUser(any());
    }

    @Test
    void sendEmailLoginCode_skipsAddressesThatCanNeitherSignInNorSignUp() {
        when(userRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtAsc("new@example.com"))
                .thenReturn(Optional.empty());
        when(accountAccessService.mayCreateAccount("new@example.com")).thenReturn(false);

        authService.sendEmailLoginCode("new@example.com");

        verify(emailLoginCodeService, never()).sendCode(any());
    }

    @Test
    void sendEmailLoginCode_sendsToExistingAccountsEvenWhenSignupIsClosed() {
        when(userRepository.findFirstByEmailIgnoreCaseOrderByCreatedAtAsc("user@example.com"))
                .thenReturn(Optional.of(persistedUser(UUID.randomUUID())));

        authService.sendEmailLoginCode("user@example.com");

        verify(emailLoginCodeService).sendCode("user@example.com");
    }

    @Test
    void everySignIn_checksWhetherTheOwnerNeedsAdmin() {
        User user = persistedUser(UUID.randomUUID());
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(mock(Authentication.class));
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(refreshTokenService.createRefreshToken(user)).thenReturn(refreshTokenFor(user));

        authService.login(new LoginRequest("user@example.com", "password123"));

        verify(accountAccessService).ensureOwnerIsAdmin(user);
    }
}
