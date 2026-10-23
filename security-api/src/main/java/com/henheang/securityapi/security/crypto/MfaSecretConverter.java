package com.henheang.securityapi.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Applied to User.mfaSecret (see @Convert there) so the Base32 TOTP secret is
// encrypted at rest rather than stored as plaintext. Spring Boot registers
// its bean container with Hibernate automatically, so a @Component converter
// referenced by class still gets constructor injection.
@Component
@RequiredArgsConstructor
@Converter(autoApply = false)
public class MfaSecretConverter implements AttributeConverter<String, String> {

    private final MfaSecretEncryptor encryptor;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return attribute == null ? null : encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return dbData == null ? null : encryptor.decrypt(dbData);
    }
}
