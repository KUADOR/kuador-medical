package com.kuador.medical.platform.subscription;

import com.kuador.medical.shared.tenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Subscription lifecycle job (ADR-016): applies the deadlines reached (end of trial, of period, of
 * grace and of suspension) and sends the price change notices (plan D1). Under a distributed lock
 * (ShedLock, the scheduling lock of the api: one run at a time across replicas), each clinic is
 * processed in its own transaction and tenant context; a failure in one clinic does not stop the
 * others. Idempotent: every transition takes its deadline as its instant and every notice is unique
 * per price version, so extra or late runs change nothing more.
 */
@Component
public class SubscriptionLifecycleJob {

    /** Name of the lock in the {@code shedlock} table. */
    public static final String LOCK_NAME = "platform-subscription-lifecycle";

    private static final Logger log = LoggerFactory.getLogger(SubscriptionLifecycleJob.class);

    private final SubscriptionDirectory directory;
    private final SubscriptionLifecycle lifecycle;
    private final TransactionTemplate transaction;
    private final LockingTaskExecutor locks;
    private final Duration lockAtMostFor;

    SubscriptionLifecycleJob(
            SubscriptionDirectory directory,
            SubscriptionLifecycle lifecycle,
            PlatformTransactionManager transactionManager,
            LockingTaskExecutor locks,
            @Value("${kuador.platform.subscription-lifecycle.lock-at-most-for:PT1H}") Duration lockAtMostFor) {
        this.directory = directory;
        this.lifecycle = lifecycle;
        this.transaction = new TransactionTemplate(transactionManager);
        this.locks = locks;
        this.lockAtMostFor = lockAtMostFor;
    }

    @Scheduled(cron = "${kuador.platform.subscription-lifecycle.cron:0 15 * * * *}", zone = "UTC")
    void scheduledRun() {
        runOnce(Instant.now());
    }

    /**
     * Runs the lifecycle once at {@code now} for every clinic, unless another run holds the lock.
     *
     * @return what this call did; {@code executed} is false when the lock was taken
     */
    public Result runOnce(Instant now) {
        LockConfiguration lock = new LockConfiguration(Instant.now(), LOCK_NAME, lockAtMostFor, Duration.ZERO);
        try {
            var outcome = locks.executeWithLock(() -> processAll(now), lock);
            return outcome.wasExecuted() ? Objects.requireNonNull(outcome.getResult()) : Result.SKIPPED;
        } catch (Throwable e) {
            throw new IllegalStateException("The subscription lifecycle job failed", e);
        }
    }

    private Result processAll(Instant now) {
        int transitions = 0;
        int notices = 0;
        for (UUID tenantId : directory.tenantsInLifecycle()) {
            try {
                int[] done = TenantContext.callAs(tenantId, () -> Objects.requireNonNull(transaction.execute(status ->
                        new int[] {lifecycle.advance(now), lifecycle.noticePriceChange(now) ? 1 : 0})));
                transitions += done[0];
                notices += done[1];
            } catch (RuntimeException e) {
                log.error("Subscription lifecycle failed for tenant {}", tenantId, e);
            }
        }
        log.info("Subscription lifecycle job finished: {} transitions, {} price notices", transitions, notices);
        return new Result(true, transitions, notices);
    }

    /** Outcome of a run. */
    public record Result(boolean executed, int transitions, int notices) {
        static final Result SKIPPED = new Result(false, 0, 0);
    }
}
