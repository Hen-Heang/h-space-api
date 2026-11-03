package com.henheang.securityapi.archtest;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.henheang.securityapi.archtest.fixture.ScopedCaller;
import com.henheang.securityapi.archtest.fixture.UnscopedCaller;
import com.henheang.securityapi.archtest.fixture.repository.ScopedFixtureRepository;
import com.henheang.securityapi.archtest.fixture.repository.UnscopedFixtureRepository;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests the guard itself: a rule that silently stopped matching would let every module's
 * architecture test pass forever. Each rule must pass on correctly scoped fixtures and fail -
 * naming the offending method - on unscoped ones.
 */
class UserScopedRepositoryRulesTest {

    private static final String REPOSITORIES =
            "com.henheang.securityapi.archtest.fixture.repository";

    private static JavaClasses importing(Class<?>... types) {
        return new ClassFileImporter().importClasses(types);
    }

    @Test
    void inheritedCalls_scopedCaller_passes() {
        JavaClasses classes = importing(ScopedCaller.class, ScopedFixtureRepository.class);

        assertThatCode(
                        () ->
                                UserScopedRepositoryRules.noUnscopedInheritedCalls(
                                                REPOSITORIES, Set.of())
                                        .check(classes))
                .doesNotThrowAnyException();
    }

    @Test
    void inheritedCalls_unscopedCallAndMethodReference_fail() {
        JavaClasses classes = importing(UnscopedCaller.class, ScopedFixtureRepository.class);

        assertThatThrownBy(
                        () ->
                                UserScopedRepositoryRules.noUnscopedInheritedCalls(
                                                REPOSITORIES, Set.of())
                                        .check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("UnscopedCaller -> ScopedFixtureRepository.findById")
                // javac records a method reference against the declaring interface
                .hasMessageContaining("UnscopedCaller -> CrudRepository.count");
    }

    @Test
    void inheritedCalls_exemptedCalls_pass() {
        JavaClasses classes = importing(UnscopedCaller.class, ScopedFixtureRepository.class);
        Set<String> exemptions =
                Set.of(
                        "UnscopedCaller -> ScopedFixtureRepository.findById",
                        "UnscopedCaller -> CrudRepository.count");

        assertThatCode(
                        () ->
                                UserScopedRepositoryRules.noUnscopedInheritedCalls(
                                                REPOSITORIES, exemptions)
                                        .check(classes))
                .doesNotThrowAnyException();
    }

    @Test
    void declaredMethods_scopedRepository_passes() {
        JavaClasses classes = importing(ScopedFixtureRepository.class);

        assertThatCode(
                        () ->
                                UserScopedRepositoryRules.declaredMethodsAreScoped(
                                                REPOSITORIES, Set.of())
                                        .check(classes))
                .doesNotThrowAnyException();
    }

    @Test
    void declaredMethods_eachKindOfUnscopedMethod_fails() {
        JavaClasses classes = importing(UnscopedFixtureRepository.class);

        assertThatThrownBy(
                        () ->
                                UserScopedRepositoryRules.declaredMethodsAreScoped(
                                                REPOSITORIES, Set.of())
                                        .check(classes))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining(
                        "UnscopedFixtureRepository.findAllByEmail has no UUID userId parameter")
                .hasMessageContaining(
                        "UnscopedFixtureRepository.byEmailIgnoringOwner takes userId but its"
                                + " @Query never uses :userId")
                .hasMessageContaining(
                        "UnscopedFixtureRepository.findAllByName takes userId but its derived"
                                + " query doesn't filter on UserId");
    }

    @Test
    void declaredMethods_exemptedMethods_pass() {
        JavaClasses classes = importing(UnscopedFixtureRepository.class);
        Set<String> exemptions =
                Set.of(
                        "UnscopedFixtureRepository.findAllByEmail",
                        "UnscopedFixtureRepository.byEmailIgnoringOwner",
                        "UnscopedFixtureRepository.findAllByName");

        assertThatCode(
                        () ->
                                UserScopedRepositoryRules.declaredMethodsAreScoped(
                                                REPOSITORIES, exemptions)
                                        .check(classes))
                .doesNotThrowAnyException();
    }
}
