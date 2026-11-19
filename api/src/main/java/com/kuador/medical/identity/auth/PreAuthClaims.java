package com.kuador.medical.identity.auth;

import java.util.List;
import java.util.UUID;

/**
 * A valid pre-auth token (ADR-012): the clinics where the password matched, grouped by the batch
 * that makes the token single use ({@link PreAuthTokens}).
 */
record PreAuthClaims(UUID batchId, List<PreAuthCandidate> candidates) {

    PreAuthClaims {
        candidates = List.copyOf(candidates);
    }
}
