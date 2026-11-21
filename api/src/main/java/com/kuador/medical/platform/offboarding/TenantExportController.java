package com.kuador.medical.platform.offboarding;

import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.platform.offboarding.OffboardingRequests.CreateExportRequest;
import com.kuador.medical.platform.offboarding.OffboardingResponses.TenantExportResponse;
import com.kuador.medical.shared.security.AllowedWhenReadOnly;
import com.kuador.medical.shared.security.RequiresPermission;
import com.kuador.medical.shared.web.ApiException;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /tenant/exports} (MEDK-70, FR-021): the clinic exports its own data with
 * {@code tenant.data.export}, also while it is read-only (SUSPENDED, or CANCELLED during retention).
 */
@RestController
@RequiresPermission("tenant.data.export")
class TenantExportController {

    private final TenantExportService exports;
    private final CurrentUser currentUser;

    TenantExportController(TenantExportService exports, CurrentUser currentUser) {
        this.exports = exports;
        this.currentUser = currentUser;
    }

    @AllowedWhenReadOnly
    @PostMapping("/tenant/exports")
    ResponseEntity<TenantExportResponse> request(
            @RequestHeader(name = "Idempotency-Key", required = false) @Nullable String idempotencyKey,
            @Valid @RequestBody CreateExportRequest request) {
        TenantExportResponse export = exports.request(request, idempotencyKey, caller());
        return ResponseEntity.accepted().location(URI.create("/api/v1/tenant/exports/" + export.id())).body(export);
    }

    @GetMapping("/tenant/exports/{exportId}")
    TenantExportResponse get(@PathVariable UUID exportId) {
        return exports.get(exportId, caller());
    }

    private AuthenticatedAccount caller() {
        return currentUser.current().orElseThrow(ApiException::unauthenticated);
    }
}
