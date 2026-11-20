/**
 * Patients: administrative registration of patients (the clinical record is another epic),
 * personal data consent (LOPDP), guardians of minors and their links, and the optional
 * self-registration of each clinic (MEDK-17), and the transition to adulthood with the links an
 * adult patient authorizes on {@code /me/guardian-links} (MEDK-18). Accounts are created only through
 * {@link com.kuador.medical.identity.AccountProvisioning}; account ids are stored without foreign key.
 */
@ApplicationModule(displayName = "Patients")
package com.kuador.medical.patients;

import org.springframework.modulith.ApplicationModule;
