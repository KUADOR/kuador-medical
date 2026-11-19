package com.kuador.medical.identity.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.identity.AccountScope;
import com.nimbusds.jwt.SignedJWT;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** HS256 access tokens (ADR-006, ADR-013); pre-auth and refresh tokens are opaque. */
class AccessTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-19T12:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(15);

    private final JwtProperties properties = properties(randomSecret());
    private final AccessTokenService service = new AccessTokenService(properties, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void tenantTokenCarriesClinicBranchAndThePermissionsOfThatAssignment() throws Exception {
        UUID account = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        UUID branch = UUID.randomUUID();

        IssuedToken token = service.issue(
                new AccessGrant(account, AccountScope.TENANT, tenant, branch, List.of("patients.read", "roles.read")));

        assertThat(token.expiresInSeconds()).isEqualTo(900);
        assertThat(service.verify(token.value()))
                .contains(new AccessTokenClaims(account, AccountScope.TENANT, tenant, branch));
        var claims = SignedJWT.parse(token.value()).getJWTClaimsSet();
        assertThat(claims.getStringClaim("tid")).isEqualTo(tenant.toString());
        assertThat(claims.getStringClaim("bid")).isEqualTo(branch.toString());
        assertThat(claims.getStringListClaim("permissions")).containsExactly("patients.read", "roles.read");
    }

    @Test
    void tenantTokenWithoutBranchIsValid() {
        UUID account = UUID.randomUUID();
        UUID tenant = UUID.randomUUID();

        IssuedToken token = service.issue(new AccessGrant(account, AccountScope.TENANT, tenant, null, List.of()));

        assertThat(service.verify(token.value()))
                .contains(new AccessTokenClaims(account, AccountScope.TENANT, tenant, null));
    }

    @Test
    void platformTokenHasNoClinic() {
        UUID account = UUID.randomUUID();

        IssuedToken token = service.issue(new AccessGrant(account, AccountScope.PLATFORM, null, null, List.of()));

        assertThat(service.verify(token.value()))
                .contains(new AccessTokenClaims(account, AccountScope.PLATFORM, null, null));
    }

    @Test
    void expiredTokenIsRejected() {
        String token = platformToken(service);
        var later = new AccessTokenService(properties, Clock.fixed(NOW.plus(TTL), ZoneOffset.UTC));

        assertThat(later.verify(token)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        var other = new AccessTokenService(properties(randomSecret()), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(service.verify(platformToken(other))).isEmpty();
    }

    @Test
    void unsignedOrMalformedTokensAreRejected() {
        String[] parts = platformToken(service).split("\\.");
        String unsigned = "eyJhbGciOiJub25lIn0." + parts[1] + ".";

        assertThat(service.verify(unsigned)).isEmpty();
        assertThat(service.verify("not-a-jwt")).isEmpty();
        assertThat(service.verify("")).isEmpty();
    }

    @Test
    void shortOrMissingSecretPreventsStartup() {
        assertThatThrownBy(() -> properties("too-short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("too-short");
        assertThatThrownBy(() -> properties(null)).isInstanceOf(IllegalStateException.class);
        assertThat(properties.toString()).doesNotContain(properties.secret());
    }

    private static String platformToken(AccessTokenService tokens) {
        return tokens.issue(new AccessGrant(UUID.randomUUID(), AccountScope.PLATFORM, null, null, List.of())).value();
    }

    private static JwtProperties properties(String secret) {
        return new JwtProperties(secret, TTL, Duration.ofDays(7), Duration.ofMinutes(5));
    }

    private static String randomSecret() {
        return UUID.randomUUID() + "" + UUID.randomUUID();
    }
}
