package com.henheang.securityapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

class MfaServiceImplTest {

    private final MfaServiceImpl mfaService = new MfaServiceImpl();
    private final String secret = mfaService.generateSecret();

    private static long currentStep() {
        return Math.floorDiv(new SystemTimeProvider().getTime(), 30);
    }

    private String codeFor(long step) throws Exception {
        return new DefaultCodeGenerator().generate(secret, step);
    }

    @Test
    void matchTimeStep_answersTheStepTheCodeBelongsTo() throws Exception {
        long step = currentStep();

        OptionalLong current = mfaService.matchTimeStep(secret, codeFor(step));
        OptionalLong next = mfaService.matchTimeStep(secret, codeFor(step + 1));

        // Between steps if the clock ticks over mid-test.
        assertThat(current).isPresent();
        assertThat(current.getAsLong()).isBetween(step - 1, step + 1);
        assertThat(next).isPresent();
        assertThat(next.getAsLong()).isBetween(step, step + 1);
    }

    @Test
    void matchTimeStep_refusesCodesOutsideTheWindow() throws Exception {
        long step = currentStep();

        assertThat(mfaService.matchTimeStep(secret, codeFor(step - 5))).isEmpty();
        assertThat(mfaService.matchTimeStep(secret, codeFor(step + 5))).isEmpty();
        assertThat(mfaService.matchTimeStep(secret, "")).isEmpty();
        assertThat(mfaService.matchTimeStep(secret, null)).isEmpty();
    }
}
