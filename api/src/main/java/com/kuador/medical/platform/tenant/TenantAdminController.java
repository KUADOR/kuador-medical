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
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final CurrentUser currentUser;

    TenantAdminController(TenantOnboardingService tenants, CurrentUser currentUser) {
        this.tenants = tenants;
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

    private AuthenticatedAccount caller() {
        return currentUser.current().orElseThrow(ApiException::unauthenticated);
    }
}
