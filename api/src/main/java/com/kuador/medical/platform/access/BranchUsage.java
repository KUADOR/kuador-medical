package com.kuador.medical.platform.access;

import com.kuador.medical.clinics.BranchDirectory;
import com.kuador.medical.platform.api.PlanUsageProvider;
import com.kuador.medical.platform.plan.LimitCode;
import org.springframework.stereotype.Component;

/**
 * Usage of {@code MAX_BRANCHES}: the branches of the clinic, read through the public API of the
 * clinics module. Creating branches belongs to a later task of that module, which will check
 * {@code PlanCapabilities.assertCanCreate(tenantId, MAX_BRANCHES)} before creating one.
 */
@Component
class BranchUsage implements PlanUsageProvider {

    private final BranchDirectory branches;

    BranchUsage(BranchDirectory branches) {
        this.branches = branches;
    }

    @Override
    public LimitCode limit() {
        return LimitCode.MAX_BRANCHES;
    }

    @Override
    public long currentUsage() {
        return branches.list().size();
    }
}
