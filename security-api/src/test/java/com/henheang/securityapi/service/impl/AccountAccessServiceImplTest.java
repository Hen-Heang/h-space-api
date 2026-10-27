package com.henheang.securityapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.henheang.apicore.components.common.api.ExitCode;
import com.henheang.securityapi.domain.Role;
import com.henheang.securityapi.domain.SignupInvite;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.domain.UserRole;
import com.henheang.securityapi.exception.AuthException;
import com.henheang.securityapi.exception.BadRequestException;
import com.henheang.securityapi.repository.SignupInviteRepository;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.service.AccountAccessService;
import com.henheang.securityapi.service.AuditLogService;
import com.henheang.securityapi.service.EmailService;
import com.henheang.securityapi.service.RoleService;
import com.henheang.securityapi.service.UserRoleService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AccountAccessServiceImplTest {

    private static final String OWNER = "Owner@Example.com";

    private SignupInviteRepository inviteRepository;
    private UserRepository userRepository;
    private RoleService roleService;
    private UserRoleService userRoleService;
    private EmailService emailService;
    private Role adminRole;

    @BeforeEach
    void setUp() {
        inviteRepository = mock(SignupInviteRepository.class);
        userRepository = mock(UserRepository.class);
        roleService = mock(RoleService.class);
        userRoleService = mock(UserRoleService.class);
        emailService = mock(EmailService.class);
        adminRole = new Role();
        adminRole.setName("ROLE_ADMIN");
        when(roleService.getOrCreateRole("ROLE_ADMIN")).thenReturn(adminRole);
        when(inviteRepository.findFirstByEmailAndUsedAtIsNull(anyString()))
                .thenReturn(Optional.empty());
        when(inviteRepository.save(any(SignupInvite.class))).thenAnswer(i -> i.getArgument(0));
    }

    private AccountAccessService service(String mode) {
        return new AccountAccessServiceImpl(
                inviteRepository,
                userRepository,
                roleService,
                userRoleService,
                emailService,
                mock(AuditLogService.class),
                OWNER,
                mode,
                "https://app.example.com");
    }

    private User user(String email, boolean verified) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setEmailVerified(verified);
        return user;
    }

    @Test
    void openMode_letsAnyoneCreateAnAccount() {
        assertThat(service("open").mayCreateAccount("anyone@example.com")).isTrue();
    }

    @Test
    void inviteOnly_refusesAnUninvitedAddress() {
        AccountAccessService service = service("invite-only");

        assertThat(service.mayCreateAccount("anyone@example.com")).isFalse();
        assertThatThrownBy(() -> service.assertMayCreateAccount("anyone@example.com"))
                .isInstanceOf(AuthException.class)
                .satisfies(
                        ex ->
                                assertThat(((AuthException) ex).getExitCode())
                                        .isEqualTo(ExitCode.SIGNUP_INVITE_REQUIRED));
    }

    @Test
    void inviteOnly_acceptsAPendingInviteCaseInsensitively() {
        when(inviteRepository.findFirstByEmailAndUsedAtIsNull("friend@example.com"))
                .thenReturn(Optional.of(new SignupInvite()));

        assertThatCode(() -> service("invite-only").assertMayCreateAccount(" Friend@Example.com"))
                .doesNotThrowAnyException();
    }

    @Test
    void anUnrecognizedMode_failsClosed() {
        assertThat(service("opne").mayCreateAccount("anyone@example.com")).isFalse();
    }

    @Test
    void theOwner_needsNoInvite() {
        assertThat(service("invite-only").mayCreateAccount("owner@example.com")).isTrue();
    }

    @Test
    void recordAccountCreated_usesUpThePendingInvite() {
        SignupInvite invite = new SignupInvite();
        when(inviteRepository.findFirstByEmailAndUsedAtIsNull("friend@example.com"))
                .thenReturn(Optional.of(invite));
        User created = user("Friend@example.com", false);

        service("invite-only").recordAccountCreated(created);

        assertThat(invite.getUsedAt()).isNotNull();
        assertThat(invite.getUsedBy()).isEqualTo(created.getId());
    }

    @Test
    void ensureOwnerIsAdmin_grantsAdminToTheVerifiedOwner() {
        User owner = user("owner@example.com", true);

        service("invite-only").ensureOwnerIsAdmin(owner);

        verify(userRoleService).assignDefaultRole(owner, adminRole);
    }

    @Test
    void ensureOwnerIsAdmin_waitsUntilTheOwnersEmailIsVerified() {
        service("invite-only").ensureOwnerIsAdmin(user("owner@example.com", false));

        verify(userRoleService, never()).assignDefaultRole(any(), any());
    }

    @Test
    void ensureOwnerIsAdmin_ignoresEveryoneElse() {
        service("invite-only").ensureOwnerIsAdmin(user("someone@example.com", true));

        verify(userRoleService, never()).assignDefaultRole(any(), any());
    }

    @Test
    void ensureOwnerIsAdmin_skipsAnOwnerWhoIsAlreadyAdmin() {
        User owner = user("owner@example.com", true);
        owner.getUserRoles().add(new UserRole(owner, adminRole, null));

        service("invite-only").ensureOwnerIsAdmin(owner);

        verify(userRoleService, never()).assignDefaultRole(any(), any());
    }

    @Test
    void withNoOwnerConfigured_nobodyIsTheOwner() {
        AccountAccessService service =
                new AccountAccessServiceImpl(
                        inviteRepository,
                        userRepository,
                        roleService,
                        userRoleService,
                        emailService,
                        mock(AuditLogService.class),
                        "",
                        "invite-only",
                        "https://app.example.com");

        assertThat(service.isOwner("")).isFalse();
        assertThat(service.isOwner("owner@example.com")).isFalse();
    }

    @Test
    void invite_storesTheEmailLowercaseAndSendsASignupLink() {
        when(emailService.sendInviteEmail(anyString(), anyString())).thenReturn(true);
        UUID adminId = UUID.randomUUID();

        AccountAccessService.InviteResult result =
                service("invite-only").invite(" Friend@Example.com ", adminId);

        assertThat(result.invite().getEmail()).isEqualTo("friend@example.com");
        assertThat(result.invite().getInvitedBy()).isEqualTo(adminId);
        assertThat(result.emailSent()).isTrue();
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendInviteEmail(eq("friend@example.com"), url.capture());
        assertThat(url.getValue())
                .isEqualTo("https://app.example.com/register?email=friend%40example.com");
    }

    @Test
    void invite_forAnAlreadyInvitedAddress_resendsTheSameInvite() {
        SignupInvite pending = new SignupInvite();
        pending.setEmail("friend@example.com");
        when(inviteRepository.findFirstByEmailAndUsedAtIsNull("friend@example.com"))
                .thenReturn(Optional.of(pending));

        AccountAccessService.InviteResult result =
                service("invite-only").invite("friend@example.com", UUID.randomUUID());

        assertThat(result.invite()).isSameAs(pending);
        verify(inviteRepository, never()).save(any());
    }

    @Test
    void revokeInvite_refusesAUsedInvite() {
        SignupInvite used = new SignupInvite();
        used.setUsedAt(Instant.now());
        UUID id = UUID.randomUUID();
        when(inviteRepository.findById(id)).thenReturn(Optional.of(used));

        assertThatThrownBy(() -> service("invite-only").revokeInvite(id))
                .isInstanceOf(BadRequestException.class);
        verify(inviteRepository, never()).delete(any());
    }
}
