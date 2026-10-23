package com.henheang.securityapi.payload.mfa;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

// Plaintext backup codes - only ever returned once, immediately after
// generation (MFA enable or explicit regenerate). Never persisted or
// returned again afterwards; the server only stores their BCrypt hashes.
@Getter
@AllArgsConstructor
public class MfaBackupCodesResponse {
    private List<String> backupCodes;
}
