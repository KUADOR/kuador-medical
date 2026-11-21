package com.kuador.medical.platform.settings;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Configurable periods of the subscription lifecycle (plan D10), defaults 14, 7, 90, 90, 72 and
 * 30. It is also the {@code PlatformSettings} body of the contract; {@code version} versions the
 * settings as a whole (optimistic locking).
 */
public record PlatformSettings(
        @NotNull @Min(1) @Max(365) Integer trialDays,
        @NotNull @Min(0) @Max(90) Integer graceDays,
        @NotNull @Min(1) @Max(365) Integer suspensionDaysBeforeCancel,
        @NotNull @Min(1) @Max(3650) Integer retentionDays,
        @NotNull @Min(1) @Max(720) Integer ownerInvitationTtlHours,
        @NotNull @Min(0) @Max(365) Integer priceChangeNoticeDays,
        @NotNull @Min(0) Long version) {}
