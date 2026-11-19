package com.kuador.medical.identity.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Lifetimes of the single-use tokens (ADR-014). */
@ConfigurationProperties("kuador.one-time-tokens")
record OneTimeTokenProperties(
        @DefaultValue("PT72H") Duration invitationTtl,
        @DefaultValue("PT30M") Duration passwordResetTtl,
        @DefaultValue("PT24H") Duration emailVerificationTtl) {}
