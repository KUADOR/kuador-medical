package com.kuador.medical.platform.plan;

import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.CurrentUser;
import com.kuador.medical.platform.plan.PlanRequests.CreatePlan;
import com.kuador.medical.platform.plan.PlanRequests.CreatePrice;
import com.kuador.medical.platform.plan.PlanRequests.UpdatePlan;
import com.kuador.medical.platform.plan.PlanResponses.LimitInfo;
import com.kuador.medical.platform.plan.PlanResponses.ModuleInfo;
import com.kuador.medical.platform.plan.PlanResponses.PlanResponse;
import com.kuador.medical.platform.plan.PlanResponses.PriceResponse;
import com.kuador.medical.shared.security.RequiresPermission;
import com.kuador.medical.shared.web.ApiException;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * {@code /platform/plans} and {@code /platform/catalog}: plans, prices and the fixed catalogs of
 * modules and limits (MEDK-65). Only the {@code platform.plans.manage} permission, which no clinic
 * role can hold, grants access; a clinic token is forbidden.
 */
@RestController
@RequiresPermission("platform.plans.manage")
class PlanController {

    private static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final PlanService plans;
    private final CurrentUser currentUser;

    PlanController(PlanService plans, CurrentUser currentUser) {
        this.plans = plans;
        this.currentUser = currentUser;
    }

    @GetMapping("/platform/plans")
    List<PlanResponse> list(@RequestParam(required = false) @Nullable PlanStatus status) {
        return plans.list(status);
    }

    @PostMapping("/platform/plans")
    ResponseEntity<PlanResponse> create(
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Nullable String idempotencyKey,
            @Valid @RequestBody CreatePlan request) {
        PlanResponse created = plans.create(request, idempotencyKey, caller());
        return ResponseEntity.created(location("/{id}", created.id())).body(created);
    }

    @GetMapping("/platform/plans/{planId}")
    PlanResponse get(@PathVariable UUID planId) {
        return plans.get(planId);
    }

    @PutMapping("/platform/plans/{planId}")
    PlanResponse update(@PathVariable UUID planId, @Valid @RequestBody UpdatePlan request) {
        return plans.update(planId, request, caller());
    }

    @PostMapping("/platform/plans/{planId}/archive")
    PlanResponse archive(@PathVariable UUID planId) {
        return plans.archive(planId, caller());
    }

    @GetMapping("/platform/plans/{planId}/prices")
    List<PriceResponse> prices(
            @PathVariable UUID planId, @RequestParam(required = false) @Nullable BillingPeriod billingPeriod) {
        return plans.priceHistory(planId, billingPeriod);
    }

    @PostMapping("/platform/plans/{planId}/prices")
    ResponseEntity<PriceResponse> createPrice(
            @PathVariable UUID planId,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) @Nullable String idempotencyKey,
            @Valid @RequestBody CreatePrice request) {
        PriceResponse created = plans.createPrice(planId, request, idempotencyKey, caller());
        return ResponseEntity.created(location("/{id}", created.id())).body(created);
    }

    @GetMapping("/platform/catalog/modules")
    List<ModuleInfo> modules() {
        return Arrays.stream(ModuleCode.values()).map(code -> new ModuleInfo(code, code.label())).toList();
    }

    @GetMapping("/platform/catalog/limits")
    List<LimitInfo> limits() {
        return Arrays.stream(LimitCode.values()).map(code -> new LimitInfo(code, code.label(), code.unit())).toList();
    }

    private AuthenticatedAccount caller() {
        return currentUser.current().orElseThrow(ApiException::unauthenticated);
    }

    private static URI location(String path, UUID id) {
        return ServletUriComponentsBuilder.fromCurrentRequest().path(path).buildAndExpand(id).toUri();
    }
}
