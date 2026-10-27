package com.henheang.securityapi.service.impl;

import com.henheang.apicore.components.common.api.ExitCode;
import com.henheang.securityapi.domain.AuditEventType;
import com.henheang.securityapi.domain.SignupInvite;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.exception.AuthException;
import com.henheang.securityapi.exception.BadRequestException;
import com.henheang.securityapi.exception.ResourceNotFoundException;
import com.henheang.securityapi.repository.SignupInviteRepository;
import com.henheang.securityapi.repository.UserRepository;
import com.henheang.securityapi.service.AccountAccessService;
import com.henheang.securityapi.service.AuditLogService;
import com.henheang.securityapi.service.EmailLoginCodeService;
import com.henheang.securityapi.service.EmailService;
import com.henheang.securityapi.service.RoleService;
import com.henheang.securityapi.service.UserRoleService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AccountAccessServiceImpl implements AccountAccessService {

    private static final Logger logger = LoggerFactory.getLogger(AccountAccessServiceImpl.class);
    private static final String ADMIN_ROLE = "ROLE_ADMIN";
    private static final String OPEN_MODE = "open";

    private final SignupInviteRepository signupInviteRepository;
    private final UserRepository userRepository;
    private final RoleService roleService;
    private final UserRoleService userRoleService;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final String ownerEmail;
    private final boolean signupOpen;
    private final String frontendUrl;

    @Autowired
    public AccountAccessServiceImpl(
            SignupInviteRepository signupInviteRepository,
            UserRepository userRepository,
            RoleService roleService,
            UserRoleService userRoleService,
            EmailService emailService,
            AuditLogService auditLogService,
            @Value("${app.owner-email:}") String ownerEmail,
            @Value("${app.signup.mode:invite-only}") String signupMode,
            @Value("${app.frontend-url:http://localhost:3000}") String frontendUrl) {
        this.signupInviteRepository = signupInviteRepository;
        this.userRepository = userRepository;
        this.roleService = roleService;
        this.userRoleService = userRoleService;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
        this.ownerEmail = EmailLoginCodeService.normalizeEmail(ownerEmail);
        // Anything but an explicit "open" is invite-only, so a typo fails closed.
        this.signupOpen = OPEN_MODE.equalsIgnoreCase(signupMode.trim());
        this.frontendUrl = frontendUrl;
        logger.info(
                "Sign-up mode: {}; owner email {}",
                signupOpen ? "open" : "invite-only",
                StringUtils.hasText(this.ownerEmail) ? "configured" : "not configured");
    }

    @Override
    public boolean isOwner(String email) {
        return StringUtils.hasText(ownerEmail)
                && ownerEmail.equals(EmailLoginCodeService.normalizeEmail(email));
    }

    @Override
    public boolean mayCreateAccount(String email) {
        return signupOpen
                || isOwner(email)
                || signupInviteRepository
                        .findFirstByEmailAndUsedAtIsNull(
                                EmailLoginCodeService.normalizeEmail(email))
                        .isPresent();
    }

    @Override
    public void assertMayCreateAccount(String email) {
        if (!mayCreateAccount(email)) {
            throw new AuthException(
                    ExitCode.SIGNUP_INVITE_REQUIRED,
                    "Sign-up is by invitation only. Ask the owner for an invite to this email.");
        }
    }

    @Override
    @Transactional
    public void recordAccountCreated(User user) {
        signupInviteRepository
                .findFirstByEmailAndUsedAtIsNull(
                        EmailLoginCodeService.normalizeEmail(user.getEmail()))
                .ifPresent(
                        invite -> {
                            invite.setUsedAt(Instant.now());
                            invite.setUsedBy(user.getId());
                            signupInviteRepository.save(invite);
                        });
    }

    @Override
    @Transactional
    public void ensureOwnerIsAdmin(User user) {
        // Verified only: whoever registers the owner's address first can't
        // get admin without proving they hold the mailbox.
        if (!isOwner(user.getEmail()) || !Boolean.TRUE.equals(user.getEmailVerified())) {
            return;
        }
        boolean alreadyAdmin =
                user.getRoles().stream().anyMatch(role -> ADMIN_ROLE.equals(role.getName()));
        if (alreadyAdmin) {
            return;
        }
        userRoleService.assignDefaultRole(user, roleService.getOrCreateRole(ADMIN_ROLE));
        logger.info("Granted {} to the owner account {}", ADMIN_ROLE, user.getId());
    }

    @Override
    @Transactional
    public void ensureOwnerIsAdmin() {
        if (StringUtils.hasText(ownerEmail)) {
            userRepository
                    .findFirstByEmailIgnoreCaseOrderByCreatedAtAsc(ownerEmail)
                    .ifPresent(this::ensureOwnerIsAdmin);
        }
    }

    @Override
    @Transactional
    public InviteResult invite(String rawEmail, UUID invitedBy) {
        String email = EmailLoginCodeService.normalizeEmail(rawEmail);
        SignupInvite invite =
                signupInviteRepository
                        .findFirstByEmailAndUsedAtIsNull(email)
                        .orElseGet(
                                () -> {
                                    SignupInvite created = new SignupInvite();
                                    created.setEmail(email);
                                    created.setInvitedBy(invitedBy);
                                    return signupInviteRepository.save(created);
                                });

        String signupUrl =
                frontendUrl + "/register?email=" + URLEncoder.encode(email, StandardCharsets.UTF_8);
        boolean emailSent = emailService.sendInviteEmail(email, signupUrl);
        if (!emailSent) {
            logger.warn("Invite saved but its email to {} failed to send", email);
        }
        auditLogService.log(AuditEventType.SIGNUP_INVITE_SENT, invitedBy, email);
        return new InviteResult(invite, emailSent);
    }

    @Override
    public List<SignupInvite> listInvites() {
        return signupInviteRepository.findAllByOrderByCreatedAtDesc();
    }

    @Override
    @Transactional
    public void revokeInvite(UUID inviteId) {
        SignupInvite invite =
                signupInviteRepository
                        .findById(inviteId)
                        .orElseThrow(() -> new ResourceNotFoundException("Invite", "id", inviteId));
        if (!invite.isPending()) {
            // A used invite is the record of how an account was created.
            throw new BadRequestException("That invite was already used.");
        }
        signupInviteRepository.delete(invite);
        logger.info("Revoked invite for {}", invite.getEmail());
    }
}
