package com.kuador.medical.platform.offboarding;

import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.platform.offboarding.OffboardingRequests.ConfirmPurgeRequest;
import com.kuador.medical.platform.offboarding.OffboardingRequests.CreatePurgeRequest;
import com.kuador.medical.platform.offboarding.OffboardingResponses.PurgeRequestResponse;
import com.kuador.medical.shared.security.RequiresPermission;
import com.kuador.medical.shared.web.ApiException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /platform/tenants/{tenantId}/purge-requests} (MEDK-70, ADR-018, plan D6): request and
 * confirmation of the purge of a clinic. Only {@code platform.tenants.purge}, which no clinic role
 * can hold.
 */
@RestController
@RequiresPermission("platform.tenants.purge")
class TenantPurgeController {

    private final TenantPurgeService purges;
    private final CurrentUser currentUser;

    TenantPurgeController(TenantPurgeService purges, CurrentUser currentUser) {
        this.purges = purges;
        this.currentUser = currentUser;
    }

    @PostMapping("/platform/tenants/{tenantId}/purge-requests")
    ResponseEntity<PurgeRequestResponse> request(
            @PathVariable UUID tenantId,
            @RequestHeader(name = "Idempotency-Key", required = false) @Nullable String idempotencyKey,
            @Valid @RequestBody CreatePurgeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(purges.request(tenantId, request, idempotencyKey, caller()));
    }

    @PostMapping("/platform/tenants/{tenantId}/purge-requests/{requestId}/confirm")
    PurgeRequestResponse confirm(
            @PathVariable UUID tenantId, @PathVariable UUID requestId, @Valid @RequestBody ConfirmPurgeRequest request) {
        return purges.confirm(tenantId, requestId, request, caller());
    }

    private AuthenticatedAccount caller() {
        return currentUser.current().orElseThrow(ApiException::unauthenticated);
    }
}
