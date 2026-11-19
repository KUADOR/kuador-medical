package com.kuador.medical.clinics;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Branches of the current clinic. Every method reads only the tenant bound in the current
 * request (ADR-007): a branch of another clinic is never returned.
 */
public interface BranchDirectory {

    /** Every branch of the current clinic, ordered by name. */
    List<Branch> list();

    /** The branches of the current clinic among {@code ids}; unknown ids are left out. */
    List<Branch> findAll(Collection<UUID> ids);

    /** A branch as other modules see it. */
    record Branch(UUID id, String name) {}
}
