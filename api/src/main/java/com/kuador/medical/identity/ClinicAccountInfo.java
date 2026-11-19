package com.kuador.medical.identity;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** A clinic account as other modules see it: no credentials, no personal data. */
public record ClinicAccountInfo(UUID id, AccountKind kind, ClinicAccountStatus status, @Nullable UUID patientRef) {}
