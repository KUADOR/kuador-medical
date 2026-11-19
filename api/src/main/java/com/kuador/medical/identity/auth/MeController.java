package com.kuador.medical.identity.auth;

import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.identity.TenantDirectory;
import com.kuador.medical.shared.security.AllowedWhilePasswordChangeRequired;
import com.kuador.medical.shared.security.AuthenticatedEndpoint;
import com.kuador.medical.shared.web.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /me}: the caller, its clinic, roles and effective permissions. */
@RestController
class MeController {

    private final CurrentUser currentUser;
    private final TenantDirectory tenants;

    MeController(CurrentUser currentUser, TenantDirectory tenants) {
        this.currentUser = currentUser;
        this.tenants = tenants;
    }

    @AuthenticatedEndpoint
    @AllowedWhilePasswordChangeRequired
    @GetMapping("/me")
    CurrentAccountResponse me() {
        AuthenticatedAccount account = currentUser.current().orElseThrow(ApiException::unauthenticated);
        CurrentAccountResponse.TenantSummary tenant = account.tenantId() == null
                ? null
                : tenants.find(account.tenantId())
                        .map(found -> new CurrentAccountResponse.TenantSummary(found.id(), found.name()))
                        .orElseThrow(ApiException::unauthenticated);
        return new CurrentAccountResponse(
                account.accountId(), account.displayName(), account.scope(), tenant,
                account.roles(), account.permissions(), account.mustChangePassword());
    }
}
