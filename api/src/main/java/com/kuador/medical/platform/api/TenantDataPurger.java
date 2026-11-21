package com.kuador.medical.platform.api;

/**
 * SPI: the module that owns data of a clinic deletes or anonymizes it when the platform purges the
 * clinic (ADR-018). Called once, inside the transaction of the confirmation, with the clinic bound
 * in the tenant context (ADR-007); a failure rolls the whole purge back.
 *
 * <p>Purgers of clinical records are skipped until the legal retention period of the clinical
 * record (MSP, LOPDP) is validated (MEDK-49 FR-024): their data is kept.
 */
public interface TenantDataPurger {

    /** Name for the audit trail and the logs, for example {@code platform.clinic}. */
    String name();

    /** Whether this purger deletes clinical records, whose purge is disabled (ADR-018). */
    default boolean purgesClinicalRecords() {
        return false;
    }

    /** Deletes or anonymizes the data of the clinic bound in the tenant context. */
    void purge();
}
