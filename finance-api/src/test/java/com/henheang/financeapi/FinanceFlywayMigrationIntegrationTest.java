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
                                + " WHERE version IN ('1', '2') AND success",
                        Integer.class);
        assertThat(applied).isEqualTo(2);

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

    private UUID insertCategoryReturningId(UUID userId, String name) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO finance_categories (id, user_id, name) VALUES (?, ?, ?)",
                id,
                userId,
                name);
        return id;
    }

    private void insertTransaction(
            UUID userId,
            UUID categoryId,
            UUID recurringId,
            String currency,
            String amountUsd,
            String rate) {
        jdbcTemplate.update(
                "INSERT INTO finance_transactions (id, user_id, date, type, description,"
                        + " currency, amount_krw, amount_usd, exchange_rate, category_id,"
                        + " recurring_id) VALUES (?, ?, DATE '2026-11-03', 'expense', 'Test', ?,"
                        + " 1000, CAST(? AS numeric), CAST(? AS numeric), ?, ?)",
                UUID.randomUUID(),
                userId,
                currency,
                amountUsd,
                rate,
                categoryId,
                recurringId);
    }

    private UUID insertRecurringRule(UUID userId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO finance_recurring_transactions (id, user_id, type, description,"
                        + " amount_krw, frequency, next_date, anchor_day)"
                        + " VALUES (?, ?, 'expense', 'Test rent', 500000, 'monthly',"
                        + " DATE '2026-12-01', 1)",
                id,
                userId);
        return id;
    }

    @Test
    void aTransactionCannotPointAtAnotherUsersCategory() {
        UUID alice = newUser();
        UUID bob = newUser();
        UUID bobsCategory = insertCategoryReturningId(bob, "Bob Food");

        assertThatThrownBy(() -> insertTransaction(alice, bobsCategory, null, "KRW", null, null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_finance_transactions_category");
        insertTransaction(bob, bobsCategory, null, "KRW", null, null);
    }

    @Test
    void enforcesTheCurrencyShape() {
        UUID alice = newUser();

        assertThatThrownBy(() -> insertTransaction(alice, null, null, "KRW", "10.00", "1390"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertTransaction(alice, null, null, "USD", "10.00", null))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertTransaction(alice, null, null, "EUR", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertTransaction(alice, null, null, "USD", "10.00", "100");
    }

    @Test
    void deletingARecurringRuleKeepsItsTransactions() {
        UUID alice = newUser();
        UUID rule = insertRecurringRule(alice);
        insertTransaction(alice, null, rule, "KRW", null, null);

        // One occurrence per rule per day.
        assertThatThrownBy(() -> insertTransaction(alice, null, rule, "KRW", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        jdbcTemplate.update("DELETE FROM finance_recurring_transactions WHERE id = ?", rule);

        Integer kept =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM finance_transactions"
                                + " WHERE user_id = ? AND recurring_id IS NULL",
                        Integer.class,
                        alice);
        assertThat(kept).isEqualTo(1);
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
