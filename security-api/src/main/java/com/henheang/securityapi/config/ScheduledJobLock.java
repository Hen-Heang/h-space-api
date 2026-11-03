package com.henheang.securityapi.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lets only one process run a scheduled job at a time. security-api's jobs also run inside hub-api,
 * finance-api and open-api (they component-scan {@code com.henheang.securityapi}), and any module
 * may run as more than one container, so every instance's scheduler fires at the same moment.
 *
 * <p>Uses a PostgreSQL <em>transaction-scoped</em> advisory lock ({@code
 * pg_try_advisory_xact_lock}) keyed by {@code hashtext(jobName)}. Postgres releases it itself at
 * commit or rollback, so there's no unlock call to forget and no risk of unlocking on a different
 * pooled connection than the one that locked (a session-level lock taken and released through a
 * connection pool can silently stay held). That's why the caller must already be in a transaction:
 * the lock lives exactly as long as the job's work.
 *
 * <p>Known limitations:
 *
 * <ul>
 *   <li>It stops <em>concurrent</em> runs, not repeated ones: an instance whose clock fires a few
 *       seconds after another has committed will run the job again. Jobs must be idempotent.
 *   <li>{@code hashtext} is a 32-bit hash, so two job names could in theory share a key; the cost
 *       of that is one job skipping a run, not a wrong result.
 *   <li>A job holding the lock keeps its transaction (and a pool connection) open for its whole
 *       run, so keep locked jobs short - batch large work.
 * </ul>
 */
@Component
public class ScheduledJobLock {

    @PersistenceContext private EntityManager entityManager;

    /**
     * Tries to take the job's lock for the rest of the current transaction.
     *
     * @return true if this instance should run the job, false if another one is running it now
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryAcquire(String jobName) {
        Object acquired =
                entityManager
                        .createNativeQuery("SELECT pg_try_advisory_xact_lock(hashtext(?1))")
                        .setParameter(1, jobName)
                        .getSingleResult();
        return Boolean.TRUE.equals(acquired);
    }
}
