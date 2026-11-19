package com.kuador.medical.identity.account;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code kuador.one-time-tokens}: lifetimes of the single-use links (ADR-014): invitation 72 hours,
 * password reset 30 minutes and email verification 24 hours by default.
 */
@ConfigurationProperties("kuador.one-time-tokens")
public record OneTimeTokenProperties(
        @DefaultValue("PT72H") Duration invitationTtl,
        @DefaultValue("PT30M") Duration passwordResetTtl,
        @DefaultValue("PT24H") Duration emailVerificationTtl) {}
