package com.kuador.medical.identity;

import java.time.Duration;

/**
 * Port to the platform settings, implemented by the platform module: how long an OWNER invitation
 * is valid ({@code OWNER_INVITATION_TTL_HOURS}, 72 hours by default, plan D10).
 */
public interface OwnerInvitationPolicy {

    Duration ownerInvitationTtl();
}
