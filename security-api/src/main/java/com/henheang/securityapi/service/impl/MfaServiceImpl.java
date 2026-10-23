package com.henheang.securityapi.service.impl;

import com.henheang.securityapi.service.MfaService;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MfaServiceImpl implements MfaService {

    private static final Logger logger = LoggerFactory.getLogger(MfaServiceImpl.class);
    private static final String ISSUER = "AuthHub";

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeVerifier codeVerifier =
            new DefaultCodeVerifier(new DefaultCodeGenerator(), timeProvider);
    private final QrGenerator qrGenerator = new ZxingPngQrGenerator();

    @Override
    public String generateSecret() {
        return secretGenerator.generate();
    }

    @Override
    public String getOtpAuthUri(String accountEmail, String secret) {
        QrData data =
                new QrData.Builder().label(accountEmail).secret(secret).issuer(ISSUER).build();
        return data.getUri();
    }

    @Override
    public String generateQrCodeImageBase64(String accountEmail, String secret) {
        try {
            QrData data =
                    new QrData.Builder().label(accountEmail).secret(secret).issuer(ISSUER).build();
            byte[] png = qrGenerator.generate(data);
            return Base64.getEncoder().encodeToString(png);
        } catch (QrGenerationException e) {
            // Setup can still proceed with the raw otpauth:// URI (most
            // authenticator apps also accept manual entry), so this is a
            // degraded response, not a failure.
            logger.warn("Failed to render MFA QR code image: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public boolean verifyCode(String secret, String code) {
        return StringUtils.hasText(code) && codeVerifier.isValidCode(secret, code);
    }
}
