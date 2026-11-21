package com.kuador.medical.platform.tenant;

import com.kuador.medical.platform.api.TenantDataExporter;
import com.kuador.medical.shared.tenancy.TenantContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Section {@code platform.clinic}: the data of the clinic kept by the platform (FR-007). */
@Component
class ClinicDataExporter implements TenantDataExporter {

    private final TenantRepository tenants;

    ClinicDataExporter(TenantRepository tenants) {
        this.tenants = tenants;
    }

    @Override
    public String section() {
        return "platform.clinic";
    }

    @Override
    public List<Map<String, Object>> rows() {
        return tenants.findById(TenantContext.current().orElseThrow()).map(tenant -> {
            ClinicData data = tenant.data();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", tenant.getId());
            row.put("name", tenant.getName());
            row.put("legalName", data.legalName());
            row.put("tradeName", data.tradeName());
            row.put("ruc", data.ruc());
            row.put("clinicType", data.clinicType());
            row.put("countryCode", data.countryCode());
            row.put("city", data.city());
            row.put("address", data.address());
            row.put("phone", data.phone());
            row.put("contactEmail", data.contactEmail());
            row.put("mspEstablishmentNumber", data.mspEstablishmentNumber());
            row.put("createdAt", tenant.getCreatedAt());
            return List.<Map<String, Object>>of(row);
        }).orElse(List.of());
    }
}
