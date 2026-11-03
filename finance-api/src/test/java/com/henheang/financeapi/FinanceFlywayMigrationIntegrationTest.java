package com.henheang.financeapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.henheang.securityapi.domain.AuthProvider;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V1__finance_lookups.sql applied through FinanceFlywayConfig: its own history table, nothing in
 * security-api's, and the constraints Luyra only enforced in code. Not @Transactional - each
 * rejected insert must fail on its own; the users created here are deleted afterwards (cascading to
 * their finance rows).
 */
@SpringBootTest
class FinanceFlywayMigrationIntegrationTest {

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;

    private final List<UUID> createdUsers = new ArrayList<>();

    @AfterEach
    void deleteUsers() {
        createdUsers.forEach(userRepository::deleteById);
    }

    private UUID newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        User user = new User();
        user.setEmail("migration-" + suffix + "@example.com");
        user.setUsername("m" + suffix);
        user.setProvider(AuthProvider.LOCAL);
        user.setEmailVerified(true);
        UUID id = userRepository.saveAndFlush(user).getId();
        createdUsers.add(id);
        return id;
    }

    private void insertCategory(UUID userId, String name, String type, String color) {
        jdbcTemplate.update(
                "INSERT INTO finance_categories (id, user_id, name, type, color)"
                        + " VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                userId,
                name,
                type,
                color);
    }

    @Test
    void appliesFinanceMigrationsInTheirOwnHistoryTable() {
        Integer applied =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM flyway_finance_schema_history"
                                + " WHERE version = '1' AND success",
                        Integer.class);
        assertThat(applied).isEqualTo(1);

        Integer inSecurityHistory =
                jdbcTemplate.queryForObject(
                        // Match migration scripts only: Flyway's schema-creation
                        // row stores the schema name (finance_api_test) here.
                        "SELECT count(*) FROM flyway_schema_history"
                                + " WHERE script LIKE 'V%finance%.sql'",
                        Integer.class);
        assertThat(inSecurityHistory).isZero();
    }

    @Test
    void enforcesOneNamePerUserIgnoringCase() {
        UUID alice = newUser();
        UUID bob = newUser();
        insertCategory(alice, "Test Food", "expense", null);
        insertCategory(bob, "test food", "expense", null);

        assertThatThrownBy(() -> insertCategory(alice, "TEST FOOD", "expense", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_finance_categories_user_name");
    }

    @Test
    void rejectsValuesOutsideTheLuyraContract() {
        UUID alice = newUser();

        assertThatThrownBy(() -> insertCategory(alice, "Test A", "transfer", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCategory(alice, "Test B", "expense", "red"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCategory(alice, "   ", "expense", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertCategory(alice, "Test C", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void providesTheCompositeKeyLaterForeignKeysWillReference() {
        Integer constraints =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM pg_constraint WHERE contype = 'u'"
                                + " AND connamespace = current_schema()::regnamespace"
                                + " AND conname IN"
                                + " ('uq_finance_categories_id_user',"
                                + " 'uq_finance_payment_methods_id_user')",
                        Integer.class);
        assertThat(constraints).isEqualTo(2);
    }
}
