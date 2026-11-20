package com.kuador.medical.patients.registration;

import com.kuador.medical.identity.TenantDirectory;
import com.kuador.medical.shared.tenancy.TenantContext;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled transition to adulthood (MEDK-18). Under a distributed lock (ShedLock), it goes over
 * the active clinics and processes each one with its own tenant context (exception 2 to principle
 * I of the plan of MEDK-12: it iterates tenants but never mixes their data). It runs every hour
 * by default so each clinic is processed soon after midnight in its own time zone; the work is
 * idempotent, so extra runs change nothing.
 */
@Component
public class AdulthoodJob {

    /** Name of the lock in the {@code shedlock} table. */
    public static final String LOCK_NAME = "patients-adulthood";

    private static final Logger log = LoggerFactory.getLogger(AdulthoodJob.class);

    private final TenantDirectory tenants;
    private final AdulthoodTransition transition;
    private final LockingTaskExecutor locks;
    private final Duration lockAtMostFor;

    AdulthoodJob(
            TenantDirectory tenants,
            AdulthoodTransition transition,
            LockingTaskExecutor locks,
            @Value("${kuador.patients.adulthood.lock-at-most-for:PT1H}") Duration lockAtMostFor) {
        this.tenants = tenants;
        this.transition = transition;
        this.locks = locks;
        this.lockAtMostFor = lockAtMostFor;
    }

    @Scheduled(cron = "${kuador.patients.adulthood.cron:0 5 * * * *}", zone = "UTC")
    void scheduledRun() {
        runOnce();
    }

    /**
     * Runs the transition once for every active clinic, unless another replica holds the lock.
     *
     * @return whether this call ran the transition ({@code false} when the lock was taken)
     */
    public boolean runOnce() {
        LockConfiguration lock = new LockConfiguration(Instant.now(), LOCK_NAME, lockAtMostFor, Duration.ZERO);
        try {
            return locks.executeWithLock(this::processAllTenants, lock).wasExecuted();
        } catch (Throwable e) {
            throw new IllegalStateException("The adulthood job failed", e);
        }
    }

    private Integer processAllTenants() {
        int total = 0;
        for (UUID tenantId : tenants.activeTenantIds()) {
            try {
                total += TenantContext.callAs(tenantId, transition::processCurrentTenant);
            } catch (RuntimeException e) {
                log.error("Adulthood transition failed for tenant {}", tenantId, e);
            }
        }
        log.info("Adulthood job finished: {} patients became adult", total);
        return total;
    }
}
