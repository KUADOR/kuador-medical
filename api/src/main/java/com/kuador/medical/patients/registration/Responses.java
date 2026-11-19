package com.kuador.medical.patients.registration;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Response bodies of the patients endpoints (schemas of the contract with the same name). */
final class Responses {

    private Responses() {}

    record ConsentResponse(
            UUID id,
            String textVersion,
            Instant grantedAt,
            ConsentGrantor grantedBy,
            ConsentChannel channel,
            @Nullable Instant revokedAt) {

        static ConsentResponse of(Consent consent) {
            return new ConsentResponse(consent.getId(), consent.getTextVersion(), consent.getGrantedAt(),
                    consent.getGrantedBy(), consent.getChannel(), consent.getRevokedAt());
        }
    }

    record PatientResponse(
            UUID id,
            String documentType,
            String documentNumber,
            String firstNames,
            String lastNames,
            LocalDate birthDate,
            String sex,
            @Nullable String email,
            @Nullable String phone,
            @Nullable String address,
            boolean minor,
            @Nullable LocalDate adultSince,
            PatientOrigin origin,
            String accountStatus,
            @Nullable ConsentResponse currentConsent,
            Instant createdAt,
            long version) {

        @Override
        public String toString() {
            return "PatientResponse[redacted]";
        }
    }

    record PatientPage(int page, int size, long totalItems, List<PatientResponse> items) {}

    record GuardianSummary(UUID id, String firstNames, String lastNames) {}

    record GuardianLinkResponse(
            UUID id,
            GuardianSummary guardian,
            LinkType linkType,
            String relationship,
            Instant validFrom,
            @Nullable Instant validTo,
            @Nullable LinkEndReason endReason,
            boolean current,
            @Nullable UUID authorizedBy) {

        static GuardianLinkResponse of(GuardianLink link, Guardian guardian) {
            return new GuardianLinkResponse(
                    link.getId(),
                    new GuardianSummary(guardian.getId(), guardian.getFirstNames(), guardian.getLastNames()),
                    link.getLinkType(),
                    link.getRelationship(),
                    link.getValidFrom(),
                    link.getValidTo(),
                    link.getEndReason(),
                    link.isCurrent(),
                    link.getAuthorizedBy());
        }
    }

    record RegistrationSettingsResponse(boolean selfRegistrationEnabled, String publicCode, long version) {}
}
