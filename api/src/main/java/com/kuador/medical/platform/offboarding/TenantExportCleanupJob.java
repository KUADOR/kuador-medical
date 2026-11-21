package com.kuador.medical.platform.offboarding;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Drops the files of the exports past their expiry (MEDK-70), so the data of a clinic does not stay
 * in the database longer than {@code kuador.platform.exports.retention}. Under the ShedLock lock of
 * the api: one run at a time across replicas. Idempotent.
 */
@Component
class TenantExportCleanupJob {

    static final String LOCK_NAME = "platform-tenant-export-cleanup";

    private static final Logger log = LoggerFactory.getLogger(TenantExportCleanupJob.class);

    private final ExportFiles files;
    private final LockingTaskExecutor locks;

    TenantExportCleanupJob(ExportFiles files, LockingTaskExecutor locks) {
        this.files = files;
        this.locks = locks;
    }

    @Scheduled(cron = "${kuador.platform.exports.cleanup-cron:0 45 * * * *}", zone = "UTC")
    void scheduledRun() {
        runOnce(Instant.now());
    }

    /** @return the exports expired by this run, or -1 when another run holds the lock */
    int runOnce(Instant now) {
        LockConfiguration lock = new LockConfiguration(Instant.now(), LOCK_NAME, Duration.ofMinutes(10), Duration.ZERO);
        try {
            var outcome = locks.executeWithLock(() -> files.expire(now), lock);
            int expired = outcome.wasExecuted() ? Objects.requireNonNull(outcome.getResult()) : -1;
            if (expired > 0) {
                log.info("Tenant export cleanup: {} expired exports dropped", expired);
            }
            return expired;
        } catch (Throwable e) {
            throw new IllegalStateException("The tenant export cleanup job failed", e);
        }
    }
}
