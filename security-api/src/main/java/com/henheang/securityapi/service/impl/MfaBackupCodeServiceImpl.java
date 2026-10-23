package com.henheang.securityapi.service.impl;

import com.henheang.securityapi.domain.MfaRecoveryCode;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.MfaRecoveryCodeRepository;
import com.henheang.securityapi.service.MfaBackupCodeService;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MfaBackupCodeServiceImpl implements MfaBackupCodeService {

    // Excludes visually ambiguous characters (0/O, 1/I).
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_COUNT = 10;
    private static final int CODE_LENGTH = 10;

    private final MfaRecoveryCodeRepository mfaRecoveryCodeRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional
    public List<String> regenerate(User user) {
        mfaRecoveryCodeRepository.deleteByUser(user);

        List<String> plaintextCodes = new ArrayList<>(CODE_COUNT);
        List<MfaRecoveryCode> recoveryCodes = new ArrayList<>(CODE_COUNT);
        for (int i = 0; i < CODE_COUNT; i++) {
            String code = generateCode();
            plaintextCodes.add(code);

            MfaRecoveryCode recoveryCode = new MfaRecoveryCode();
            recoveryCode.setUser(user);
            recoveryCode.setCodeHash(passwordEncoder.encode(normalize(code)));
            recoveryCode.setUsed(false);
            recoveryCodes.add(recoveryCode);
        }
        mfaRecoveryCodeRepository.saveAll(recoveryCodes);
        return plaintextCodes;
    }

    @Override
    @Transactional
    public boolean verifyAndConsume(User user, String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        String normalized = normalize(code);
        for (MfaRecoveryCode recoveryCode :
                mfaRecoveryCodeRepository.findByUserAndUsedFalse(user)) {
            if (passwordEncoder.matches(normalized, recoveryCode.getCodeHash())) {
                recoveryCode.setUsed(true);
                recoveryCode.setUsedAt(Instant.now());
                mfaRecoveryCodeRepository.save(recoveryCode);
                return true;
            }
        }
        return false;
    }

    @Override
    @Transactional
    public void clear(User user) {
        mfaRecoveryCodeRepository.deleteByUser(user);
    }

    private String generateCode() {
        StringBuilder raw = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            raw.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        // Cosmetic split, e.g. "ABCD2-EFGH3" - stripped back off before hashing/matching.
        return raw.substring(0, CODE_LENGTH / 2) + "-" + raw.substring(CODE_LENGTH / 2);
    }

    private String normalize(String code) {
        return code.replace("-", "").trim().toUpperCase();
    }
}
