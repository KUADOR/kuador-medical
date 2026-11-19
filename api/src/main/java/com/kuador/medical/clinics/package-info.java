/**
 * Clinics and branches. In epic MEDK-12 it only exposes the minimal read API identity needs
 * (plan of MEDK-12, precondition): {@link com.kuador.medical.clinics.BranchDirectory}, and the
 * {@link com.kuador.medical.clinics.ClinicCalendar} the patients module needs (MEDK-17).
 * Creating and editing branches belongs to a later task.
 */
@ApplicationModule(displayName = "Clinics")
package com.kuador.medical.clinics;

import org.springframework.modulith.ApplicationModule;
