package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.AccountScope;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies the HS256 tokens of ADR-006, signed with {@code KUADOR_JWT_SECRET}.
 *
 * Claims: {@code use=access}, {@code sub} (account), {@code scope}, {@code tid} (clinic, only
 * TENANT), {@code bid} (active branch, when there is one) and {@code permissions} of that
 * assignment (ADR-013), and {@code tfa}: the second factor verified by the session (ADR-019), if
 * any. Pre-auth and refresh tokens are opaque and live elsewhere.
 */
@Component
public class AccessTokenService {

    static final String ISSUER = "medical-kuador";
    private static final String USE_CLAIM = "use";
    private static final String ACCESS_USE = "access";
    private static final String SCOPE_CLAIM = "scope";
    private static final String TENANT_CLAIM = "tid";
    private static final String BRANCH_CLAIM = "bid";
    private static final String PERMISSIONS_CLAIM = "permissions";
    private static final String TWO_FACTOR_CLAIM = "tfa";

    private final JWSSigner signer;
    private final JWSVerifier verifier;
    private final JwtProperties properties;
    private final Clock clock;

    @Autowired
    public AccessTokenService(JwtProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AccessTokenService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        try {
            this.signer = new MACSigner(properties.secretBytes());
            this.verifier = new MACVerifier(properties.secretBytes());
        } catch (JOSEException e) {
            throw new IllegalStateException("Invalid kuador.jwt.secret (KUADOR_JWT_SECRET)", e);
        }
    }

    IssuedToken issue(AccessGrant grant) {
        if ((grant.scope() == AccountScope.TENANT) != (grant.tenantId() != null)
                || (grant.branchId() != null && grant.tenantId() == null)) {
            throw new IllegalArgumentException("A TENANT token needs a clinic; a PLATFORM token has no clinic nor branch");
        }
        JWTClaimsSet.Builder claims = base(ACCESS_USE, grant.accountId().toString(), properties.ttl())
                .claim(SCOPE_CLAIM, grant.scope().name())
                .claim(PERMISSIONS_CLAIM, grant.permissions());
        if (grant.tenantId() != null) {
            claims.claim(TENANT_CLAIM, grant.tenantId().toString());
        }
        if (grant.branchId() != null) {
            claims.claim(BRANCH_CLAIM, grant.branchId().toString());
        }
        if (grant.twoFactorId() != null) {
            claims.claim(TWO_FACTOR_CLAIM, grant.twoFactorId().toString());
        }
        return sign(claims, properties.ttl());
    }

    /** Claims of a valid access token; empty when it is malformed, tampered, expired, foreign or not an access token. */
    public Optional<AccessTokenClaims> verify(String token) {
        return safely(() -> verified(token, ACCESS_USE).flatMap(claims -> {
            UUID accountId = UUID.fromString(claims.getSubject());
            AccountScope scope = AccountScope.valueOf(stringClaim(claims, SCOPE_CLAIM));
            UUID tenant = uuidClaim(claims, TENANT_CLAIM);
            UUID branch = uuidClaim(claims, BRANCH_CLAIM);
            UUID twoFactor = uuidClaim(claims, TWO_FACTOR_CLAIM);
            if ((scope == AccountScope.TENANT) != (tenant != null) || (branch != null && tenant == null)) {
                return Optional.empty();
            }
            return Optional.of(new AccessTokenClaims(accountId, scope, tenant, branch, twoFactor));
        }));
    }

    /** A malformed claim (bad UUID, unknown scope, missing value) makes the token invalid. */
    private static <T> Optional<T> safely(java.util.function.Supplier<Optional<T>> reading) {
        try {
            return reading.get();
        } catch (IllegalArgumentException | NullPointerException | ClassCastException e) {
            return Optional.empty();
        }
    }

    private JWTClaimsSet.Builder base(String use, String subject, Duration ttl) {
        Instant now = clock.instant();
        return new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(subject)
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .claim(USE_CLAIM, use);
    }

    private IssuedToken sign(JWTClaimsSet.Builder claims, Duration ttl) {
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot sign the token", e);
        }
        return new IssuedToken(jwt.serialize(), ttl.toSeconds());
    }

    private Optional<JWTClaimsSet> verified(String token, String use) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (!ISSUER.equals(claims.getIssuer())
                    || !use.equals(claims.getStringClaim(USE_CLAIM))
                    || expiration == null
                    || !expiration.toInstant().isAfter(clock.instant())) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    private static String stringClaim(JWTClaimsSet claims, String name) {
        try {
            return claims.getStringClaim(name);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid claim " + name, e);
        }
    }

    private static @Nullable UUID uuidClaim(JWTClaimsSet claims, String name) {
        String value = stringClaim(claims, name);
        return value == null ? null : UUID.fromString(value);
    }
}
