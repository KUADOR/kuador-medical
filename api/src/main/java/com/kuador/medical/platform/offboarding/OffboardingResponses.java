package com.kuador.medical.platform.offboarding;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Response bodies of the purge and the data export ({@code platform.openapi.yaml}). */
final class OffboardingResponses {

    private OffboardingResponses() {}

    /** {@code PurgeRequest}. */
    record PurgeRequestResponse(UUID id, TenantPurgeRequest.Status status, Instant requestedAt, @Nullable Instant confirmedAt) {

        static PurgeRequestResponse of(TenantPurgeRequest request) {
            return new PurgeRequestResponse(request.getId(), request.getStatus(), request.getRequestedAt(),
                    request.getConfirmedAt());
        }
    }

    /** {@code TenantExport}: {@code downloadUrl} only while {@code READY}. */
    record TenantExportResponse(
            UUID id,
            ExportFormat format,
            TenantExport.Status status,
            Instant createdAt,
            @Nullable Instant expiresAt,
            @Nullable String downloadUrl) {}
}
