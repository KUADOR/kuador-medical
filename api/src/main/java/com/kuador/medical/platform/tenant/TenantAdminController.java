package com.kuador.medical.platform.tenant;

import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.platform.subscription.SubscriptionStatus;
import com.kuador.medical.platform.tenant.TenantRequests.CreateTenant;
import com.kuador.medical.platform.tenant.TenantResponses.TenantPage;
import com.kuador.medical.platform.tenant.TenantResponses.TenantResponse;
import com.kuador.medical.shared.security.RequiresPermission;
import com.kuador.medical.shared.web.ApiException;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Map;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * {@code /platform/tenants}: clinic onboarding and administration (MEDK-66). Only the
 * {@code platform.tenants.manage} permission, which no clinic role can hold, grants access; a
 * clinic token is forbidden, even on its own clinic.
 */
@RestController
@RequiresPermission("platform.tenants.manage")
class TenantAdminController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final TenantOnboardingService tenants;
    private final TenantOwnerService owners;
    private final CurrentUser currentUser;

    TenantAdminController(TenantOnboardingService tenants, TenantOwnerService owners, CurrentUser currentUser) {
        this.tenants = tenants;
        this.owners = owners;
        this.currentUser = currentUser;
    }

    @GetMapping("/platform/tenants")
    TenantPage list(
            @RequestParam(required = false) @Nullable String q,
            @RequestParam(required = false) @Nullable SubscriptionStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return tenants.list(q, status, page, size);
    }

    @PostMapping("/platform/tenants")
    ResponseEntity<TenantResponse> create(
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Nullable String idempotencyKey,
            @Valid @RequestBody CreateTenant request) {
        TenantResponse created = tenants.create(request, idempotencyKey, caller());
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping("/platform/tenants/{tenantId}")
    TenantResponse get(@PathVariable UUID tenantId) {
        return tenants.get(tenantId);
    }

    @PatchMapping("/platform/tenants/{tenantId}")
    TenantResponse update(@PathVariable UUID tenantId, @RequestBody Map<String, @Nullable Object> body) {
        return tenants.update(tenantId, TenantUpdate.parse(body), caller());
    }

    /** Resends the OWNER invitation, revoking the previous one (MEDK-67). */
    @PostMapping("/platform/tenants/{tenantId}/owner-invitations")
    ResponseEntity<TenantResponses.InvitationResponse> resendOwnerInvitation(
            @PathVariable UUID tenantId,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Nullable String idempotencyKey,
            @Valid @RequestBody(required = false) TenantRequests.@Nullable ResendOwnerInvitation request) {
        TenantRequests.ResendOwnerInvitation body =
                request == null ? new TenantRequests.ResendOwnerInvitation(null, null) : request;
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(owners.resendInvitation(tenantId, body, idempotencyKey, caller()));
    }

    /** Resets the OWNER's second factor with an audited reason (plan D7). */
    @PostMapping("/platform/tenants/{tenantId}/owner/two-factor-reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void resetOwnerTwoFactor(@PathVariable UUID tenantId, @Valid @RequestBody TenantRequests.Reason request) {
        owners.resetTwoFactor(tenantId, request.reason(), caller());
    }

    private AuthenticatedAccount caller() {
        return currentUser.current().orElseThrow(ApiException::unauthenticated);
    }
}
