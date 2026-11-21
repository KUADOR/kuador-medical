package com.kuador.medical.platform.offboarding;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Request bodies of the purge and the data export ({@code platform.openapi.yaml}). */
final class OffboardingRequests {

    private OffboardingRequests() {}

    /** {@code CreatePurgeRequest}: the reason stays in the purge request, never in logs. */
    record CreatePurgeRequest(@Nullable UUID id, @NotBlank @Size(max = 500) String reason) {

        @Override
        public String toString() {
            return "CreatePurgeRequest[id=" + id + ", reason=redacted]";
        }
    }

    /** {@code ConfirmPurgeRequest}: the RUC of the clinic, typed by the SUPER_ADMIN (plan D6). */
    record ConfirmPurgeRequest(@NotNull @Pattern(regexp = "^\\d{13}$") String rucConfirmation) {

        @Override
        public String toString() {
            return "ConfirmPurgeRequest[redacted]";
        }
    }

    /** {@code CreateExportRequest}. */
    record CreateExportRequest(@Nullable UUID id, @NotNull ExportFormat format) {}
}
