package com.henheang.securityapi.controller;

import com.henheang.commonapi.components.common.api.ExitCode;
import com.henheang.securityapi.domain.AuditEventType;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.exception.AuthException;
import com.henheang.securityapi.payload.mfa.MfaBackupCodesResponse;
import com.henheang.securityapi.payload.mfa.MfaCodeRequest;
import com.henheang.securityapi.payload.mfa.MfaSetupResponse;
import com.henheang.securityapi.payload.mfa.MfaVerifyRequest;
import com.henheang.securityapi.security.UserPrincipal;
import com.henheang.securityapi.service.AuditLogService;
import com.henheang.securityapi.service.AuthService;
import com.henheang.securityapi.service.MfaBackupCodeService;
import com.henheang.securityapi.service.MfaService;
import com.henheang.securityapi.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/mfa")
@RequiredArgsConstructor
@Tag(
        name = "MFA",
        description = "TOTP-based multi-factor authentication (Google Authenticator compatible)")
public class MfaController extends BaseController {

    private final MfaService mfaService;
    private final MfaBackupCodeService mfaBackupCodeService;
    private final UserService userService;
    private final AuthService authService;
    private final AuditLogService auditLogService;

    @Operation(
            summary = "Start MFA setup",
            description =
                    "Generates a new TOTP secret and returns both the otpauth:// URI and a "
                            + "ready-to-render QR code. MFA stays disabled until /enable confirms the "
                            + "user's authenticator app actually produces valid codes. Calling this "
                            + "again before /enable replaces the pending secret.")
    @ApiResponse(
            responseCode = "200",
            description = "Secret and QR code generated",
            content =
                    @Content(
                            examples =
                                    @ExampleObject(
                                            name = "setup-response",
                                            value =
                                                    "{\"status\":{\"code\":200,\"message\":\"Success\"},"
                                                            + "\"data\":{\"secret\":\"JBSWY3DPEHPK3PXP\","
                                                            + "\"otpAuthUri\":\"otpauth://totp/AuthHub:jane@example.com?secret=JBSWY3DPEHPK3PXP&issuer=AuthHub\","
                                                            + "\"qrCodeImageBase64\":\"iVBORw0KGgoAAAANSUhEUgAA...\"}}")))
    @PostMapping("/setup")
    public Object setup(@AuthenticationPrincipal UserPrincipal userPrincipal) {
        User user = userService.getUserById(userPrincipal.getId());
        if (user.isMfaEnabled()) {
            throw new AuthException(ExitCode.MFA_ALREADY_ENABLED);
        }

        String secret = mfaService.generateSecret();
        user.setMfaSecret(secret);
        user.setMfaEnabled(false);
        userService.saveUser(user);
        auditLogService.log(AuditEventType.MFA_SETUP_INITIATED, user.getId(), user.getEmail());

        String otpAuthUri = mfaService.getOtpAuthUri(user.getEmail(), secret);
        String qrCodeImageBase64 = mfaService.generateQrCodeImageBase64(user.getEmail(), secret);
        return ok(new MfaSetupResponse(secret, otpAuthUri, qrCodeImageBase64));
    }

    @Operation(
            summary = "Enable MFA",
            description =
                    "Confirms the authenticator app from /setup by verifying one live TOTP code, "
                            + "then turns MFA on and issues a fresh set of one-time backup/recovery "
                            + "codes. The codes are returned here once only - store them securely, "
                            + "they cannot be retrieved again (only regenerated).")
    @ApiResponse(
            responseCode = "200",
            description = "MFA enabled; one-time backup codes returned",
            content =
                    @Content(
                            examples =
                                    @ExampleObject(
                                            name = "enable-response",
                                            value =
                                                    "{\"status\":{\"code\":200,\"message\":\"Success\"},"
                                                            + "\"data\":{\"backupCodes\":[\"7K2NQ-8XPRT4\",\"M3VBH-2ZQWY9\"]}}")))
    @PostMapping("/enable")
    public Object enable(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody MfaCodeRequest request) {
        User user = userService.getUserById(userPrincipal.getId());
        if (user.getMfaSecret() == null) {
            throw new AuthException(ExitCode.MFA_SETUP_REQUIRED);
        }
        if (!mfaService.verifyCode(user.getMfaSecret(), request.getCode())) {
            throw new AuthException(ExitCode.MFA_CODE_INVALID);
        }
        user.setMfaEnabled(true);
        userService.saveUser(user);

        var backupCodes = mfaBackupCodeService.regenerate(user);
        auditLogService.log(AuditEventType.MFA_ENABLED, user.getId(), user.getEmail());
        return ok(new MfaBackupCodesResponse(backupCodes));
    }

    @Operation(
            summary = "Disable MFA",
            description =
                    "Turns MFA off for the current user. Requires a currently valid TOTP or backup "
                            + "code to prove the caller still controls the authenticator, so a stolen "
                            + "session token alone can't be used to strip MFA protection. Clears the "
                            + "secret and all backup codes.")
    @ApiResponse(responseCode = "200", description = "MFA disabled")
    @PostMapping("/disable")
    public Object disable(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody MfaCodeRequest request) {
        User user = userService.getUserById(userPrincipal.getId());
        if (!user.isMfaEnabled()) {
            throw new AuthException(ExitCode.MFA_NOT_ENABLED);
        }
        if (!isValidMfaOrBackupCode(user, request.getCode())) {
            auditLogService.log(AuditEventType.MFA_DISABLE_FAILED, user.getId(), user.getEmail());
            throw new AuthException(ExitCode.MFA_CODE_INVALID);
        }

        user.setMfaEnabled(false);
        user.setMfaSecret(null);
        userService.saveUser(user);
        mfaBackupCodeService.clear(user);
        auditLogService.log(AuditEventType.MFA_DISABLED, user.getId(), user.getEmail());
        return ok();
    }

    @Operation(
            summary = "Verify MFA during login",
            description =
                    "Called after /api/auth/login returns mfaRequired=true, in place of a normal "
                            + "login response. Exchanges the short-lived MFA challenge token plus "
                            + "either a valid TOTP code or an unused backup code for real access/refresh "
                            + "tokens.")
    @ApiResponse(
            responseCode = "200",
            description = "MFA verified; access/refresh tokens issued",
            content =
                    @Content(
                            examples =
                                    @ExampleObject(
                                            name = "verify-response",
                                            value =
                                                    "{\"mfaToken\":\"eyJhbGciOi...\",\"code\":\"123456\"}")))
    @PostMapping("/verify")
    public Object verify(@Valid @RequestBody MfaVerifyRequest request) {
        return ok(authService.verifyMfaAndIssueTokens(request.getMfaToken(), request.getCode()));
    }

    @Operation(
            summary = "Regenerate backup codes",
            description =
                    "Invalidates every existing backup code and issues a fresh set of 10. Requires a "
                            + "currently valid TOTP or backup code, since this is a sensitive credential "
                            + "reset. The new codes are returned once only.")
    @ApiResponse(responseCode = "200", description = "New one-time backup codes issued")
    @PostMapping("/backup-codes/regenerate")
    public Object regenerateBackupCodes(
            @AuthenticationPrincipal UserPrincipal userPrincipal,
            @Valid @RequestBody MfaCodeRequest request) {
        User user = userService.getUserById(userPrincipal.getId());
        if (!user.isMfaEnabled()) {
            throw new AuthException(ExitCode.MFA_NOT_ENABLED);
        }
        if (!isValidMfaOrBackupCode(user, request.getCode())) {
            throw new AuthException(ExitCode.MFA_CODE_INVALID);
        }

        var backupCodes = mfaBackupCodeService.regenerate(user);
        auditLogService.log(
                AuditEventType.MFA_BACKUP_CODES_REGENERATED, user.getId(), user.getEmail());
        return ok(new MfaBackupCodesResponse(backupCodes));
    }

    private boolean isValidMfaOrBackupCode(User user, String code) {
        return mfaService.verifyCode(user.getMfaSecret(), code)
                || mfaBackupCodeService.verifyAndConsume(user, code);
    }
}
