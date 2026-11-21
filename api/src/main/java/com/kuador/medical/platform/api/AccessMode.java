package com.kuador.medical.platform.api;

import com.kuador.medical.platform.subscription.SubscriptionStatus;

/**
 * What a clinic can do according to the status of its subscription ({@code AccessMode} in
 * {@code platform.openapi.yaml}, ADR-017).
 */
public enum AccessMode {
    /** TRIAL, ACTIVE and PAST_DUE: everything the plan enables. */
    FULL,
    /** SUSPENDED: reads and data export only. */
    READ_ONLY,
    /** CANCELLED within retention: reads and data export only, until the clinic is purged. */
    RETENTION,
    /** Purged: no access. */
    NONE;

    public static AccessMode of(SubscriptionStatus status, boolean purged) {
        if (purged) {
            return NONE;
        }
        return switch (status) {
            case TRIAL, ACTIVE, PAST_DUE -> FULL;
            case SUSPENDED -> READ_ONLY;
            case CANCELLED -> RETENTION;
        };
    }

    /** Whether the clinic can create or change data. */
    public boolean allowsWrites() {
        return this == FULL;
    }
}
