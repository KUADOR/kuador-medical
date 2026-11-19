package com.kuador.medical.identity.account;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** {@code kuador.one-time-tokens}: lifetime of the invitation links (ADR-014, 72 hours by default). */
@ConfigurationProperties("kuador.one-time-tokens")
public record OneTimeTokenProperties(@DefaultValue("PT72H") Duration invitationTtl) {}
