package com.kuador.medical.shared.security;

import java.util.Set;

/**
 * Explicit list of the endpoints that need no authenticated caller (Constitution II, MEDK-26 plan,
 * decision 4). Every handler marked {@link PublicEndpoint} must appear here and vice versa
 * ({@code EndpointPermissionTest}); any other handler needs {@link AuthenticatedEndpoint} or
 * {@link RequiresPermission}, otherwise {@link AccessControlInterceptor} rejects it.
 *
 * <p>{@code GET /actuator/health} is served by Spring Boot Actuator, the only Actuator endpoint
 * exposed (management settings in {@code application.yaml}); it reports status only, no details.
 */
public final class PublicEndpoints {

    public static final Set<String> ROUTES = Set.of(
            // Login and session recovery: there is no caller before logging in.
            "POST /api/v1/auth/login",
            "POST /api/v1/auth/login/clinic",
            "POST /api/v1/auth/login/branch",
            "POST /api/v1/auth/refresh",
            "POST /api/v1/auth/logout",
            "POST /api/v1/auth/password-reset/request",
            "POST /api/v1/auth/password-reset/confirm",
            // One-time tokens sent by email (ADR-014).
            "POST /api/v1/auth/invitations/preview",
            "POST /api/v1/auth/invitations/accept",
            "POST /api/v1/auth/email-verification/confirm",
            // OWNER invitation of a clinic onboarded by the SUPER_ADMIN (MEDK-67).
            "POST /api/v1/public/owner-invitations/preview",
            "POST /api/v1/public/owner-invitations/accept",
            // Clinic self-signup and patient self-registration.
            "POST /api/v1/tenants/signup",
            "POST /api/v1/public/clinics/{publicCode}/patient-registrations",
            // Download link of a data export, signed and short-lived (MEDK-70): opened by the browser.
            "GET /api/v1/public/tenant-exports/download",
            // Status, without business data.
            "GET /api/v1/system/info",
            "GET /actuator/health");

    private PublicEndpoints() {}
}
