package com.kuador.medical.identity.owner;

import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;

/**
 * FR-009 of MEDK-49 (plan: {@code LAST_OWNER_PROTECTED}, published as {@code LAST_OWNER}): a
 * clinic always keeps an active OWNER, so the last one can be neither disabled, removed nor
 * demoted. Pure rule, shared by every path that can take the OWNER role away.
 */
public final class LastOwnerGuard {

    private LastOwnerGuard() {}

    /**
     * @param targetIsOwner whether the account about to lose the role (or be disabled) holds OWNER
     * @param targetActive  whether that account is active, so it counts among the active owners
     * @param activeOwners  active accounts of the clinic holding OWNER, the target included
     * @throws ApiException 409 {@code LAST_OWNER} when the clinic would be left without an active OWNER
     */
    public static void check(boolean targetIsOwner, boolean targetActive, long activeOwners) {
        if (targetIsOwner && targetActive && activeOwners <= 1) {
            throw ApiException.conflict(ErrorCode.LAST_OWNER, "The clinic would be left without an OWNER.");
        }
    }
}
