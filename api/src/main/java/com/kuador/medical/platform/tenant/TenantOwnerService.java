package com.kuador.medical.platform.tenant;

import com.kuador.medical.identity.AuthenticatedAccount;
import com.kuador.medical.identity.OwnerOnboarding;
import com.kuador.medical.platform.idempotency.PlatformIdempotentCreations;
import com.kuador.medical.platform.tenant.TenantResponses.InvitationResponse;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The initial OWNER of an onboarded clinic, administered by the SUPER_ADMIN (MEDK-67): resend its
 * invitation (revoking the previous one, optionally to a corrected email) and reset its second
 * factor with a reason (plan D7). Identity does the work through {@link OwnerOnboarding}, with the
 * clinic bound; purged and non-onboarded clinics are not found.
 */
@Service
class TenantOwnerService {

    static final String RESEND_OPERATION = "platform.tenants.owner-invitations.resend";

    private final TransactionTemplate transaction;
    private final TenantRepository tenants;
    private final OwnerOnboarding owners;
    private final PlatformIdempotentCreations idempotency;
    private final Clock clock = Clock.systemUTC();

    TenantOwnerService(
            PlatformTransactionManager transactionManager,
            TenantRepository tenants,
            OwnerOnboarding owners,
            PlatformIdempotentCreations idempotency) {
        this.transaction = new TransactionTemplate(transactionManager);
        this.tenants = tenants;
        this.owners = owners;
        this.idempotency = idempotency;
    }

    /** {@code POST /platform/tenants/{tenantId}/owner-invitations}. */
    InvitationResponse resendInvitation(
            UUID tenantId, TenantRequests.ResendOwnerInvitation request, @Nullable String idempotencyKey,
            AuthenticatedAccount actor) {
        return TenantContext.callAs(tenantId, () -> Objects.requireNonNull(transaction.execute(status -> {
            Tenant tenant = tenants.findOnboarded(tenantId).orElseThrow(ApiException::notFound);
            idempotency.create(actor.accountId(), RESEND_OPERATION, idempotencyKey, request, () -> {
                String email = request.email() == null ? null : request.email().strip();
                UUID invitation = owners.resendInvitation(email, actor.accountId());
                if (email != null) {
                    tenant.correctOwnerEmail(email, clock.instant());
                }
                return invitation;
            });
            return Objects.requireNonNull(InvitationResponse.of(owners.status().invitation()));
        })));
    }

    /** {@code POST /platform/tenants/{tenantId}/owner/two-factor-reset}. */
    void resetTwoFactor(UUID tenantId, String reason, AuthenticatedAccount actor) {
        TenantContext.runAs(tenantId, () -> transaction.executeWithoutResult(status -> {
            tenants.findOnboarded(tenantId).orElseThrow(ApiException::notFound);
            owners.resetTwoFactor(reason.strip(), actor.accountId());
        }));
    }
}
