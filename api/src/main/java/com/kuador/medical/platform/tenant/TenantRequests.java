package com.kuador.medical.platform.tenant;

import com.kuador.medical.platform.plan.BillingPeriod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Bodies of {@code /platform/tenants} (contracts/platform.openapi.yaml). Never logged with their values. */
final class TenantRequests {

    static final String COUNTRY_CODE = "^[A-Z]{2}$";

    private TenantRequests() {}

    /** {@code CreateTenantRequest}: every field the plan requires (FR-007) is mandatory. */
    record CreateTenant(
            @Nullable UUID id,
            @NotBlank @Size(max = 200) String legalName,
            @Size(max = 200) @Nullable String tradeName,
            @NotBlank @Pattern(regexp = RucValidator.FORMAT) String ruc,
            @NotNull ClinicType clinicType,
            @NotBlank @Pattern(regexp = COUNTRY_CODE) String countryCode,
            @NotBlank @Size(max = 120) String city,
            @NotBlank @Size(max = 300) String address,
            @NotBlank @Size(min = 7, max = 20) String phone,
            @NotBlank @Email @Size(max = 254) String contactEmail,
            @Size(max = 40) @Nullable String mspEstablishmentNumber,
            @NotNull UUID planId,
            @NotNull BillingPeriod billingPeriod,
            @NotNull @Valid OwnerInput owner) {

        ClinicData data() {
            return new ClinicData(legalName.strip(), blankToNull(tradeName), ruc, clinicType, countryCode,
                    city.strip(), address.strip(), phone.strip(), contactEmail.strip(), blankToNull(mspEstablishmentNumber));
        }

        @Override
        public String toString() {
            return "CreateTenant[redacted]";
        }
    }

    /** {@code OwnerInput}: the initial OWNER, invited by email (MEDK-67). */
    record OwnerInput(
            @NotBlank @Size(max = 160) String fullName,
            @NotBlank @Email @Size(max = 254) String email) {

        @Override
        public String toString() {
            return "OwnerInput[redacted]";
        }
    }

    /** {@code ResendOwnerInvitationRequest}: optionally to a corrected email. */
    record ResendOwnerInvitation(@Nullable UUID id, @Email @Size(max = 254) @Nullable String email) {

        @Override
        public String toString() {
            return "ResendOwnerInvitation[redacted]";
        }
    }

    /** {@code ReasonRequest}: kept with the action; must not contain clinical data. */
    record Reason(@NotBlank @Size(max = 500) String reason) {

        @Override
        public String toString() {
            return "Reason[redacted]";
        }
    }

    static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
