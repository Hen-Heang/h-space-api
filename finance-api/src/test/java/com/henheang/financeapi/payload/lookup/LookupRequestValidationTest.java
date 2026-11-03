package com.henheang.financeapi.payload.lookup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidNullException;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit tests (no Spring context) for the lookup request DTOs: JSON binding plus Bean Validation
 * must behave like Luyra's zod schemas - trim first, then check limits; defaults only for absent
 * fields; PATCH distinguishes absent from null.
 */
class LookupRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new Jdk8Module());

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    private Set<String> invalidFields(Object request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(path -> path.iterator().next().getName())
                .collect(Collectors.toSet());
    }

    @Test
    void trimsBeforeCheckingTheNameLength() throws Exception {
        String padded = "  " + "x".repeat(40) + "  ";
        CategoryRequest request =
                mapper.readValue("{\"name\":\"" + padded + "\"}", CategoryRequest.class);

        assertThat(request.getName()).hasSize(40);
        assertThat(invalidFields(request)).isEmpty();
    }

    @Test
    void defaultsTheTypeOnlyWhenAbsent() throws Exception {
        CategoryRequest request = mapper.readValue("{\"name\":\"Test\"}", CategoryRequest.class);
        assertThat(request.getType()).isEqualTo("expense");

        assertThatThrownBy(
                        () ->
                                mapper.readValue(
                                        "{\"name\":\"Test\",\"type\":null}", CategoryRequest.class))
                .isInstanceOf(InvalidNullException.class);
    }

    @Test
    void flagsEachInvalidCategoryField() throws Exception {
        CategoryRequest request =
                mapper.readValue(
                        "{\"name\":\" \",\"icon\":\" \",\"color\":\"#GGGGGG\","
                                + "\"type\":\"transfer\",\"spendingClass\":\"luxury\"}",
                        CategoryRequest.class);

        assertThat(invalidFields(request))
                .containsExactlyInAnyOrder("name", "icon", "color", "type", "spendingClass");
    }

    @Test
    void acceptsAMultiCodePointEmojiIcon() throws Exception {
        // A ZWJ family emoji is 8 UTF-16 units, the same length zod measures.
        CategoryRequest request =
                mapper.readValue(
                        "{\"name\":\"Test\",\"icon\":\"👨‍👩‍👧\"}", CategoryRequest.class);

        assertThat(request.getIcon()).hasSize(8);
        assertThat(invalidFields(request)).isEmpty();
    }

    @Test
    void patchDistinguishesAbsentFromExplicitNull() throws Exception {
        CategoryPatchRequest absent = mapper.readValue("{}", CategoryPatchRequest.class);
        assertThat(absent.isEmpty()).isTrue();

        CategoryPatchRequest cleared =
                mapper.readValue("{\"icon\":null,\"name\":\" Test \"}", CategoryPatchRequest.class);
        assertThat(cleared.isEmpty()).isFalse();
        assertThat(cleared.getIcon()).isEmpty();
        assertThat(cleared.getName()).contains("Test");
        assertThat(cleared.getColor()).isNull();
    }

    @Test
    void patchValidatesTheWrappedValue() throws Exception {
        CategoryPatchRequest request =
                mapper.readValue(
                        "{\"name\":\"" + "x".repeat(41) + "\",\"color\":\"blue\"}",
                        CategoryPatchRequest.class);

        assertThat(invalidFields(request)).containsExactlyInAnyOrder("name", "color");
    }

    @Test
    void validatesPaymentMethodRequests() throws Exception {
        PaymentMethodRequest ok =
                mapper.readValue("{\"name\":\" Test Card \"}", PaymentMethodRequest.class);
        assertThat(ok.getName()).isEqualTo("Test Card");
        assertThat(ok.getIcon()).isNull();
        assertThat(invalidFields(ok)).isEmpty();

        PaymentMethodRequest bad =
                mapper.readValue(
                        "{\"name\":\"\",\"icon\":\"123456789\"}", PaymentMethodRequest.class);
        assertThat(invalidFields(bad)).containsExactlyInAnyOrder("name", "icon");

        PaymentMethodPatchRequest patch =
                mapper.readValue("{\"icon\":null}", PaymentMethodPatchRequest.class);
        assertThat(patch.isEmpty()).isFalse();
        assertThat(patch.getName()).isNull();
    }
}
