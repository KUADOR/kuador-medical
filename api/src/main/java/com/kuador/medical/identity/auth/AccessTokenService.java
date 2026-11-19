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
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Issues and verifies the HS256 access tokens of ADR-006. Claims: {@code sub} (account),
 * {@code scope} ({@code TENANT} or {@code PLATFORM}) and {@code tid} (clinic, only for TENANT).
 */
@Component
public class AccessTokenService {

    static final String ISSUER = "medical-kuador";
    private static final String SCOPE_CLAIM = "scope";
    private static final String TENANT_CLAIM = "tid";

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

    public IssuedToken issue(UUID accountId, AccountScope scope, @Nullable UUID tenantId) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.ttl());
        var claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(accountId.toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .claim(SCOPE_CLAIM, scope.name());
        if (tenantId != null) {
            claims.claim(TENANT_CLAIM, tenantId.toString());
        }
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
        try {
            jwt.sign(signer);
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot sign the access token", e);
        }
        return new IssuedToken(jwt.serialize(), properties.ttl().toSeconds());
    }

    /** Claims of a valid token; empty when the token is malformed, tampered, expired or foreign. */
    public Optional<AccessTokenClaims> verify(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm()) || !jwt.verify(verifier)) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (!ISSUER.equals(claims.getIssuer()) || expiration == null || !expiration.toInstant().isAfter(clock.instant())) {
                return Optional.empty();
            }
            UUID accountId = UUID.fromString(claims.getSubject());
            AccountScope scope = AccountScope.valueOf(claims.getStringClaim(SCOPE_CLAIM));
            String tenant = claims.getStringClaim(TENANT_CLAIM);
            if ((scope == AccountScope.TENANT) != (tenant != null)) {
                return Optional.empty();
            }
            return Optional.of(new AccessTokenClaims(accountId, scope, tenant != null ? UUID.fromString(tenant) : null));
        } catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
