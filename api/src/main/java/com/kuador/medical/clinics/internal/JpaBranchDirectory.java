package com.kuador.medical.clinics.internal;

import com.kuador.medical.clinics.BranchDirectory;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
class JpaBranchDirectory implements BranchDirectory {

    private final ClinicBranchRepository branches;

    JpaBranchDirectory(ClinicBranchRepository branches) {
        this.branches = branches;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Branch> list() {
        return branches.findAllInCurrentTenant().stream().map(JpaBranchDirectory::toBranch).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Branch> findAll(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return branches.findInCurrentTenant(ids).stream().map(JpaBranchDirectory::toBranch).toList();
    }

    private static Branch toBranch(ClinicBranch branch) {
        return new Branch(branch.getId(), branch.getName());
    }
}
