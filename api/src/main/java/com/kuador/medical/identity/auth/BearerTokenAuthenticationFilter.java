package com.kuador.medical.identity.auth;

import com.kuador.medical.shared.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates the {@code Authorization: Bearer} token. For a TENANT token the clinic of the
 * token is bound in {@link TenantContext} for the whole request (ADR-007), so every query is
 * filtered by it. A missing, invalid or expired token leaves the request anonymous: public
 * endpoints still answer and the others respond 401.
 */
@Component
class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final AccessTokenService tokens;
    private final AccountResolver resolver;

    BearerTokenAuthenticationFilter(AccessTokenService tokens, AccountResolver resolver) {
        this.tokens = tokens;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<AccessTokenClaims> claims = bearerToken(request).flatMap(tokens::verify);
        if (claims.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        if (claims.get().tenantId() == null) {
            authenticateAndContinue(claims.get(), request, response, chain);
            return;
        }
        try {
            TenantContext.runAs(claims.get().tenantId(), () -> {
                try {
                    authenticateAndContinue(claims.get(), request, response, chain);
                } catch (IOException | ServletException e) {
                    throw new ChainFailure(e);
                }
            });
        } catch (ChainFailure failure) {
            if (failure.getCause() instanceof IOException io) {
                throw io;
            }
            throw (ServletException) failure.getCause();
        }
    }

    private void authenticateAndContinue(
            AccessTokenClaims claims, HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        resolver.resolve(claims).ifPresent(account -> RequestAccounts.bind(request, account));
        chain.doFilter(request, response);
    }

    private static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return Optional.empty();
        }
        String token = header.substring(BEARER.length()).strip();
        return token.isEmpty() ? Optional.empty() : Optional.of(token);
    }

    /** Carries a checked exception of the filter chain out of the tenant scope. */
    private static final class ChainFailure extends RuntimeException {

        ChainFailure(Exception cause) {
            super(cause);
        }
    }
}
