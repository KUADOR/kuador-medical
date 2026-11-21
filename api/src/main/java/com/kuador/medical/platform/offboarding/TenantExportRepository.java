package com.kuador.medical.platform.offboarding;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Data exports; Hibernate restricts every query to the clinic bound in the tenant context. */
interface TenantExportRepository extends JpaRepository<TenantExport, UUID> {}
