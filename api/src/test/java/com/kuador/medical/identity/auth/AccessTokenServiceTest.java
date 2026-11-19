package com.kuador.medical.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.identity.AccountScope;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** HS256 access tokens of ADR-006. */
class AccessTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(30);

    private final JwtProperties properties = new JwtProperties(randomSecret(), TTL);
    private final AccessTokenService service = new AccessTokenService(properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void tenantTokenCarriesAccountScopeAndClinic() {
        UUID account = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();

        IssuedToken token = service.issue(account, AccountScope.TENANT, tenant);

        assertThat(token.expiresInSeconds()).isEqualTo(1800);
        assertThat(service.verify(token.value())).contains(new AccessTokenClaims(account, AccountScope.TENANT, tenant));
    }

    @Test
    void platformTokenHasNoClinic() {
        UUID account = UUID.randomUUID();

        IssuedToken token = service.issue(account, AccountScope.PLATFORM, null);

        assertThat(service.verify(token.value())).contains(new AccessTokenClaims(account, AccountScope.PLATFORM, null));
    }

    @Test
    void expiredTokenIsRejected() {
        String token = service.issue(UUID.randomUUID(), AccountScope.PLATFORM, null).value();
        var later = new AccessTokenService(properties, Clock.fixed(NOW.plus(TTL), ZoneOffset.UTC));

        assertThat(later.verify(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        var other = new AccessTokenService(new JwtProperties(randomSecret(), TTL), Clock.fixed(NOW, ZoneOffset.UTC));
        String foreign = other.issue(UUID.randomUUID(), AccountScope.PLATFORM, null).value();

        assertThat(service.verify(foreign)).isEmpty();
    }

    @Test
    void unsignedOrMalformedTokensAreRejected() {
        String token = service.issue(UUID.randomUUID(), AccountScope.PLATFORM, null).value();
        String[] parts = token.split("\\.");
        String unsigned = "eyJhbGciOiJub25lIn0." + parts[1] + ".";

        assertThat(service.verify(unsigned)).isEmpty();
        assertThat(service.verify("not-a-jwt")).isEmpty();
        assertThat(service.verify("")).isEmpty();
    }

    @Test
    void shortOrMissingSecretPreventsStartup() {
        assertThatThrownBy(() -> new JwtProperties("too-short", TTL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("too-short");
        assertThatThrownBy(() -> new JwtProperties(null, TTL)).isInstanceOf(IllegalStateException.class);
        assertThat(properties.toString()).doesNotContain(properties.secret());
    }

    private static String randomSecret() {
        return UUID.randomUUID() + "" + UUID.randomUUID();
    }
}
