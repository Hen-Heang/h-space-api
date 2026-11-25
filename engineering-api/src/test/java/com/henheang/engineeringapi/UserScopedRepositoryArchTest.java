package com.henheang.engineeringapi;

import com.henheang.apicore.archtest.UserScopedRepositoryRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Every engineering-api row belongs to one owner, so every repository access must be scoped by the
 * caller's userId (the token's sub) - see {@link UserScopedRepositoryRules}. No exemptions; adding
 * one needs a comment saying why it can't reach another owner's rows.
 */
class UserScopedRepositoryArchTest {

    private static final String REPOSITORIES = "com.henheang.engineeringapi.repository";

    private static final JavaClasses PRODUCTION =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.henheang.engineeringapi");

    @Test
    void servicesNeverCallUnscopedRepositoryMethods() {
        UserScopedRepositoryRules.noUnscopedInheritedCalls(REPOSITORIES, Set.of())
                .check(PRODUCTION);
    }

    @Test
    void repositoryMethodsAreScopedByUserId() {
        UserScopedRepositoryRules.declaredMethodsAreScoped(REPOSITORIES, Set.of())
                .check(PRODUCTION);
    }
}
