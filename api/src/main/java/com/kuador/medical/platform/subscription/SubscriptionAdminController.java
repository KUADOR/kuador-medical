package com.kuador.medical.platform.subscription;

import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.platform.subscription.SubscriptionRequests.PriceLockRequest;
import com.kuador.medical.platform.subscription.SubscriptionRequests.TransitionRequest;
import com.kuador.medical.platform.subscription.SubscriptionResponses.SubscriptionResponse;
import com.kuador.medical.shared.security.RequiresPermission;
import com.kuador.medical.shared.web.ApiException;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /platform/tenants/{tenantId}/subscription}: subscription, manual transitions and price
 * lock (MEDK-68, ADR-016). Only {@code platform.tenants.manage}, which no clinic role can hold,
 * grants access; so only a SUPER_ADMIN can reactivate a cancelled clinic (plan D12).
 */
@RestController
@RequiresPermission("platform.tenants.manage")
class SubscriptionAdminController {

    private final SubscriptionAdminService subscriptions;
    private final CurrentUser currentUser;

    SubscriptionAdminController(SubscriptionAdminService subscriptions, CurrentUser currentUser) {
        this.subscriptions = subscriptions;
        this.currentUser = currentUser;
    }

    @GetMapping("/platform/tenants/{tenantId}/subscription")
    SubscriptionResponse get(@PathVariable UUID tenantId) {
        return subscriptions.get(tenantId);
    }

    @PostMapping("/platform/tenants/{tenantId}/subscription/transitions")
    SubscriptionResponse transition(@PathVariable UUID tenantId, @Valid @RequestBody TransitionRequest request) {
        return subscriptions.transition(tenantId, request, caller());
    }

    @PutMapping("/platform/tenants/{tenantId}/subscription/price-lock")
    SubscriptionResponse lockPrice(@PathVariable UUID tenantId, @Valid @RequestBody PriceLockRequest request) {
        return subscriptions.lockPrice(tenantId, request, caller());
    }

    private AuthenticatedAccount caller() {
        return currentUser.current().orElseThrow(ApiException::unauthenticated);
    }
}
