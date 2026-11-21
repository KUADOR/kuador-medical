package com.kuador.medical.platform.offboarding;

import com.kuador.medical.platform.api.TenantDataPurger;
import com.kuador.medical.shared.tenancy.TenantContext;
import org.springframework.stereotype.Component;

/** Deletes the exports of the clinic, with their files (ADR-018). */
@Component
class ExportDataPurger implements TenantDataPurger {

    private final ExportFiles files;

    ExportDataPurger(ExportFiles files) {
        this.files = files;
    }

    @Override
    public String name() {
        return "platform.exports";
    }

    @Override
    public void purge() {
        files.deleteAll(TenantContext.current().orElseThrow());
    }
}
