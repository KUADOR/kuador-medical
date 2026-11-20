package com.kuador.medical.patients.registration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Body of {@code POST /me/guardian-links} ({@code AuthorizeLinkRequest} in the contract): exactly
 * one of {@code guardianId} (for example the former guardian) or {@code person}.
 */
record AuthorizeLinkRequest(
        @Nullable UUID id,
        @Nullable UUID guardianId,
        @Nullable @Valid GuardianPersonInput person,
        @NotNull AuthorizedLinkType linkType,
        @NotBlank @Size(max = 60) String relationship) {

    /** The link types an adult patient may authorize. */
    enum AuthorizedLinkType {
        EMERGENCY_CONTACT(LinkType.EMERGENCY_CONTACT),
        DELEGATED_ACCESS(LinkType.DELEGATED_ACCESS);

        private final LinkType linkType;

        AuthorizedLinkType(LinkType linkType) {
            this.linkType = linkType;
        }

        LinkType linkType() {
            return linkType;
        }
    }

    @Override
    public String toString() {
        return "AuthorizeLinkRequest[redacted]";
    }
}
