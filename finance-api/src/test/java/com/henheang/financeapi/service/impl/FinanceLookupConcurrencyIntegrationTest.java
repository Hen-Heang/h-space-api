package com.henheang.financeapi.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.henheang.apicore.components.common.api.StatusCode;
import com.henheang.financeapi.payload.lookup.CategoryRequest;
import com.henheang.financeapi.payload.lookup.PaymentMethodRequest;
import com.henheang.financeapi.repository.FinanceCategoryRepository;
import com.henheang.financeapi.repository.FinancePaymentMethodRepository;
import com.henheang.financeapi.service.FinanceCategoryService;
import com.henheang.financeapi.service.FinancePaymentMethodService;
import com.henheang.securityapi.domain.AuthProvider;
import com.henheang.securityapi.domain.User;
import com.henheang.securityapi.exception.BusinessException;
import com.henheang.securityapi.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Two simultaneous creates of the same name (differing only in case) for one user: whichever of the
 * service's pre-check or the unique index catches it, exactly one row is created and the other
 * request gets the same 409 CONFLICT. Luyra's check-then-insert could store both. Not
 * {@code @Transactional}: each call must commit in its own transaction.
 */
@SpringBootTest
class FinanceLookupConcurrencyIntegrationTest {

    private static final int ATTEMPTS = 8;

    @Autowired private FinanceCategoryService categoryService;
    @Autowired private FinancePaymentMethodService paymentMethodService;
    @Autowired private FinanceCategoryRepository categoryRepository;
    @Autowired private FinancePaymentMethodRepository paymentMethodRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ObjectMapper objectMapper;

    private final List<UUID> createdUsers = new ArrayList<>();

    @AfterEach
    void deleteUsers() {
        // ON DELETE CASCADE removes their finance rows.
        createdUsers.forEach(userRepository::deleteById);
    }

    private UUID newUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        User user = new User();
        user.setEmail("race-" + suffix + "@example.com");
        user.setUsername("r" + suffix);
        user.setProvider(AuthProvider.LOCAL);
        user.setEmailVerified(true);
        UUID id = userRepository.saveAndFlush(user).getId();
        createdUsers.add(id);
        return id;
    }

    // Runs the calls at the same moment and returns how many succeeded; any
    // failure must be the duplicate-name conflict.
    private int runConcurrently(List<Callable<Object>> calls) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(calls.size());
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> call : calls) {
                futures.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    return call.call();
                                }));
            }
            start.countDown();
            int succeeded = 0;
            for (Future<Object> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                    succeeded++;
                } catch (java.util.concurrent.ExecutionException ex) {
                    assertThat(ex.getCause()).isInstanceOf(BusinessException.class);
                    assertThat(((BusinessException) ex.getCause()).getErrorCode())
                            .isEqualTo(StatusCode.CONFLICT);
                }
            }
            return succeeded;
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void concurrentCategoryCreatesWithTheSameNameStoreOneRow() throws Exception {
        UUID userId = newUser();
        List<Callable<Object>> calls = new ArrayList<>();
        for (int i = 0; i < ATTEMPTS; i++) {
            String name = i % 2 == 0 ? "Test Race" : "TEST race";
            CategoryRequest request =
                    objectMapper.readValue("{\"name\":\"" + name + "\"}", CategoryRequest.class);
            calls.add(() -> categoryService.create(userId, request));
        }

        assertThat(runConcurrently(calls)).isEqualTo(1);
        assertThat(categoryRepository.findAllByUserIdOrderByNameAscIdAsc(userId)).hasSize(1);
    }

    @Test
    void concurrentPaymentMethodCreatesWithTheSameNameStoreOneRow() throws Exception {
        UUID userId = newUser();
        List<Callable<Object>> calls = new ArrayList<>();
        for (int i = 0; i < ATTEMPTS; i++) {
            PaymentMethodRequest request =
                    objectMapper.readValue("{\"name\":\"Test Card\"}", PaymentMethodRequest.class);
            calls.add(() -> paymentMethodService.create(userId, request));
        }

        assertThat(runConcurrently(calls)).isEqualTo(1);
        assertThat(paymentMethodRepository.findAllByUserIdOrderByNameAscIdAsc(userId)).hasSize(1);
    }
}
