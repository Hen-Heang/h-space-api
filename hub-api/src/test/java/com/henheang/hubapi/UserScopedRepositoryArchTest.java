package com.henheang.hubapi;

import com.henheang.securityapi.archtest.UserScopedRepositoryRules;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Every hub-api row (vault items and keysets, inbox items) belongs to one user, so every repository
 * access must be scoped by the caller's userId - see {@link UserScopedRepositoryRules}. A new
 * exemption needs a comment saying why it can't reach another user's rows.
 */
class UserScopedRepositoryArchTest {

    private static final String REPOSITORIES = "com.henheang.hubapi.repository";

    private static final JavaClasses PRODUCTION =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.henheang.hubapi");

    private static final Set<String> INHERITED_CALL_EXEMPTIONS =
            Set.of(
                    // vault_keysets' primary key is user_id: looking a keyset up "by id" with the
                    // caller's own userId is the scoped lookup.
                    "VaultServiceImpl -> VaultKeysetRepository.findById",
                    "VaultServiceImpl -> VaultKeysetRepository.existsById",
                    // Deliberately global: item ids are client-chosen but must be unique across
                    // all vaults. It returns only a boolean, and the same answer whoever owns the
                    // id, so it can't be used to read or probe another user's vault.
                    "VaultServiceImpl -> VaultItemRepository.existsById");

    private static final Set<String> DECLARED_METHOD_EXEMPTIONS =
            Set.of(
                    // VaultCleanupTask's system purge of soft-deleted accounts: cross-user by
                    // design, takes no input, and never returns rows.
                    "VaultItemRepository.deleteForDeletedUsers",
                    "VaultKeysetRepository.deleteForDeletedUsers");

    @Test
    void servicesNeverCallUnscopedRepositoryMethods() {
        UserScopedRepositoryRules.noUnscopedInheritedCalls(REPOSITORIES, INHERITED_CALL_EXEMPTIONS)
                .check(PRODUCTION);
    }

    @Test
    void repositoryMethodsAreScopedByUserId() {
        UserScopedRepositoryRules.declaredMethodsAreScoped(REPOSITORIES, DECLARED_METHOD_EXEMPTIONS)
                .check(PRODUCTION);
    }
}
