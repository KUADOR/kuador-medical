package com.kuador.medical.platform.offboarding;

import com.kuador.medical.platform.subscription.SubscriptionStatus;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.jspecify.annotations.Nullable;

/**
 * When a clinic can be purged (MEDK-49 FR-021, ADR-018): only a {@code CANCELLED} clinic, not
 * purged yet, whose retention period has ended. Checked when the purge is requested and again
 * when it is confirmed.
 */
final class PurgeEligibility {

    static final ZoneId ZONE = ZoneId.of("America/Guayaquil");

    private PurgeEligibility() {}

    static boolean eligible(SubscriptionStatus status, @Nullable Instant retentionEndsAt, boolean purged, Instant now) {
        return status == SubscriptionStatus.CANCELLED && !purged && retentionEndsAt != null && !now.isBefore(retentionEndsAt);
    }

    /** @throws ApiException 409 {@code PURGE_NOT_ALLOWED} with the reason in {@code detail} */
    static void check(SubscriptionStatus status, @Nullable Instant retentionEndsAt, boolean purged, Instant now) {
        if (purged) {
            throw notAllowed("The clinic was already purged.");
        }
        if (status != SubscriptionStatus.CANCELLED) {
            throw notAllowed("Only a cancelled clinic can be purged; it is " + status + ".");
        }
        if (retentionEndsAt == null || now.isBefore(retentionEndsAt)) {
            throw notAllowed(retentionEndsAt == null ? "The clinic has no retention period."
                    : "The retention period ends on " + LocalDate.ofInstant(retentionEndsAt, ZONE) + ".");
        }
    }

    static ApiException notAllowed(String detail) {
        return ApiException.conflict(ErrorCode.PURGE_NOT_ALLOWED, detail);
    }
}
