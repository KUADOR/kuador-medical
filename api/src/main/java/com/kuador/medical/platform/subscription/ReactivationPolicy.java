package com.kuador.medical.platform.subscription;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import com.kuador.medical.shared.web.FieldViolation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Reactivation of a {@code CANCELLED} subscription (plan D12, decided by Equipo Kuador): only a
 * SUPER_ADMIN, manually (the permission and the {@code MANUAL} cause are checked by the caller and
 * {@link SubscriptionStateMachine}), with a non-blank reason, while the retention has not ended and
 * the clinic is not purged.
 */
final class ReactivationPolicy {

    private ReactivationPolicy() {}

    static boolean reactivable(SubscriptionStatus status, @Nullable Instant retentionEndsAt, boolean purged, Instant now) {
        return status == SubscriptionStatus.CANCELLED && !purged && retentionEndsAt != null && now.isBefore(retentionEndsAt);
    }

    /**
     * @throws ApiException 400 {@code VALIDATION} on {@code reason} when it is blank, or 409
     *     {@code REACTIVATION_WINDOW_CLOSED} when the retention ended or the clinic was purged
     */
    static void check(@Nullable Instant retentionEndsAt, boolean purged, @Nullable String reason, Instant now) {
        if (reason == null || reason.isBlank()) {
            throw ApiException.validation(List.of(new FieldViolation("reason", "REQUIRED")));
        }
        if (purged) {
            throw ApiException.conflict(ErrorCode.REACTIVATION_WINDOW_CLOSED, "The clinic was purged.");
        }
        if (!reactivable(SubscriptionStatus.CANCELLED, retentionEndsAt, false, now)) {
            String endedOn = retentionEndsAt == null ? "" : " on " + LocalDate.ofInstant(retentionEndsAt, InitialTerms.ZONE);
            throw ApiException.conflict(ErrorCode.REACTIVATION_WINDOW_CLOSED, "The retention period ended" + endedOn + ".");
        }
    }
}
