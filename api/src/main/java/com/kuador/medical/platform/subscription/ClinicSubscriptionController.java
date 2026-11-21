package com.kuador.medical.platform.subscription;

import com.kuador.medical.platform.subscription.SubscriptionResponses.ClinicSubscriptionResponse;
import com.kuador.medical.shared.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /tenant/subscription} (MEDK-69): the subscription of the clinic of the access token.
 * A read, so it answers in every access mode but {@code NONE} (purged).
 */
@RestController
class ClinicSubscriptionController {

    private final ClinicSubscriptionService subscriptions;

    ClinicSubscriptionController(ClinicSubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @RequiresPermission("subscription.read")
    @GetMapping("/tenant/subscription")
    ClinicSubscriptionResponse get() {
        return subscriptions.current();
    }
}
