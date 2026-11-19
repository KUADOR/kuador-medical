package com.kuador.medical.identity.auth;

import java.util.UUID;

/** A clinic where the password was verified at login, and the account of that clinic. */
record PreAuthCandidate(UUID tenantId, UUID accountId) {}
