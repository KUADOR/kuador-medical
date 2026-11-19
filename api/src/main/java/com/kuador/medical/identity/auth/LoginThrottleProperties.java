package com.kuador.medical.identity.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Login attempt limit (ADR-012): {@code maxFailures} failures inside {@code window}, per email +
 * client address and per email, lock the login for {@code lockDuration}.
 */
@ConfigurationProperties("kuador.login-throttle")
record LoginThrottleProperties(
        @DefaultValue("5") int maxFailures,
        @DefaultValue("PT15M") Duration window,
        @DefaultValue("PT15M") Duration lockDuration) {

    LoginThrottleProperties {
        if (maxFailures < 1 || window.isNegative() || window.isZero() || lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalStateException("kuador.login-throttle needs max-failures >= 1 and positive durations");
        }
    }
}
