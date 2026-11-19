package com.kuador.medical.clinics;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Calendar of the current clinic: its time zone and today's date there. Minority of patients is
 * computed with it (plan of MEDK-12). Until clinics store their own time zone, every clinic uses
 * the configured one ({@code kuador.clinics.time-zone}).
 */
public interface ClinicCalendar {

    ZoneId timeZone();

    /** Today in the time zone of the current clinic. */
    LocalDate today();
}
