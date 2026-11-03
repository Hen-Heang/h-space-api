package com.henheang.financeapi;

import com.henheang.securityapi.archtest.UserScopedRepositoryRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Every finance-api row (transactions, categories, payment methods) belongs to one user, so every
 * repository access must be scoped by the caller's userId - see {@link UserScopedRepositoryRules}.
 * There are no exemptions; adding one needs a comment saying why it can't reach another user's
 * rows.
 */
class UserScopedRepositoryArchTest {

    private static final String REPOSITORIES = "com.henheang.financeapi.repository";

    private static final JavaClasses PRODUCTION =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.henheang.financeapi");

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
