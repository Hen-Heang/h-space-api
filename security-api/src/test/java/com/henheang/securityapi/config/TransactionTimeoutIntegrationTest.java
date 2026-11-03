package com.henheang.securityapi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionTimedOutException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * spring.transaction.default-timeout puts a deadline on every transaction, and Postgres really
 * cancels a query that outlives it - so one slow query can't hold a pool connection indefinitely.
 */
class TransactionTimeoutIntegrationTest {

    @Nested
    @SpringBootTest
    class Defaults {

        @Autowired private PlatformTransactionManager transactionManager;

        @Test
        void everyTransactionGetsA30SecondDeadlineByDefault() {
            assertThat(
                            ((AbstractPlatformTransactionManager) transactionManager)
                                    .getDefaultTimeout())
                    .isEqualTo(30);
        }
    }

    @Nested
    @SpringBootTest(properties = "spring.transaction.default-timeout=1s")
    class WithAShortDeadline {

        @Autowired private TransactionTemplate transactionTemplate;
        @Autowired private JdbcTemplate jdbcTemplate;
        @PersistenceContext private EntityManager entityManager;

        @Test
        void aSlowJdbcQueryIsCancelled() {
            long started = System.nanoTime();

            assertThatThrownBy(
                            () ->
                                    transactionTemplate.executeWithoutResult(
                                            tx -> jdbcTemplate.execute("SELECT pg_sleep(5)")))
                    .isInstanceOfAny(
                            QueryTimeoutException.class, TransactionTimedOutException.class);
            assertThat(elapsedSince(started)).isLessThan(Duration.ofSeconds(4));
        }

        @Test
        void aSlowJpaQueryIsCancelled() {
            long started = System.nanoTime();

            assertThatThrownBy(
                            () ->
                                    transactionTemplate.executeWithoutResult(
                                            tx ->
                                                    entityManager
                                                            .createNativeQuery(
                                                                    "SELECT CAST(pg_sleep(5) AS text)")
                                                            .getSingleResult()))
                    // Raw EntityManager use isn't translated by a repository proxy.
                    .isInstanceOfAny(
                            jakarta.persistence.QueryTimeoutException.class,
                            QueryTimeoutException.class,
                            TransactionTimedOutException.class);
            assertThat(elapsedSince(started)).isLessThan(Duration.ofSeconds(4));
        }

        @Test
        void aFastQueryStillWorks() {
            Integer one =
                    transactionTemplate.execute(
                            tx -> jdbcTemplate.queryForObject("SELECT 1", Integer.class));
            assertThat(one).isEqualTo(1);
        }
    }

    private static Duration elapsedSince(long startedNanos) {
        return Duration.ofNanos(System.nanoTime() - startedNanos);
    }
}
