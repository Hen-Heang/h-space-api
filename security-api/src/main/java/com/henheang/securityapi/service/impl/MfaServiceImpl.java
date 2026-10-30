package com.henheang.securityapi.service.impl;

import com.henheang.securityapi.service.MfaService;
import dev.samstevens.totp.code.CodeGenerator;
import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.exceptions.CodeGenerationException;
import dev.samstevens.totp.exceptions.QrGenerationException;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.OptionalLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MfaServiceImpl implements MfaService {

    private static final Logger logger = LoggerFactory.getLogger(MfaServiceImpl.class);
    private static final String ISSUER = "H-Space-API";

    private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
    private final TimeProvider timeProvider = new SystemTimeProvider();
    private final CodeGenerator codeGenerator = new DefaultCodeGenerator();
    private final CodeVerifier codeVerifier = new DefaultCodeVerifier(codeGenerator, timeProvider);

    // DefaultCodeVerifier's defaults: 30-second steps, one step either side.
    private static final int PERIOD_SECONDS = 30;
    private static final int ALLOWED_STEP_DRIFT = 1;
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

    @Override
    public OptionalLong matchTimeStep(String secret, String code) {
        if (!StringUtils.hasText(code)) {
            return OptionalLong.empty();
        }
        byte[] given = code.getBytes(StandardCharsets.UTF_8);
        long current = Math.floorDiv(timeProvider.getTime(), PERIOD_SECONDS);
        try {
            for (long step = current - ALLOWED_STEP_DRIFT;
                    step <= current + ALLOWED_STEP_DRIFT;
                    step++) {
                byte[] expected =
                        codeGenerator.generate(secret, step).getBytes(StandardCharsets.UTF_8);
                if (MessageDigest.isEqual(expected, given)) {
                    return OptionalLong.of(step);
                }
            }
        } catch (CodeGenerationException e) {
            logger.warn("Failed to generate a TOTP code to compare: {}", e.getMessage());
        }
        return OptionalLong.empty();
    }
}
