package com.henheang.securityapi.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

// Not @Transactional: these tests control the transactions themselves. They write no rows.
@SpringBootTest
class ScheduledJobLockIntegrationTest {

    @Autowired private ScheduledJobLock jobLock;
    @Autowired private TransactionTemplate transactionTemplate;

    @Test
    void outsideATransaction_isRejected() {
        // A transaction-scoped lock taken with no transaction would be released at once.
        assertThatThrownBy(() -> jobLock.tryAcquire("lock-test-job"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void sameJob_isFreeAgain_afterTheHoldingTransactionEnds() {
        Boolean first = transactionTemplate.execute(tx -> jobLock.tryAcquire("lock-test-job"));
        Boolean second = transactionTemplate.execute(tx -> jobLock.tryAcquire("lock-test-job"));

        assertThat(first).isTrue();
        assertThat(second).isTrue();
    }
}
