package com.kuador.medical.patients.registration;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.clinics.ClinicCalendar;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/**
 * The 18th birthday is reached on the date of the clinic, not on the date of the server (plan of
 * MEDK-12, § Transición a la mayoría de edad), and the candidate window of the job agrees with
 * {@link Patient#isMinor(LocalDate, LocalDate)} also for people born on February 29.
 */
class AdulthoodDateTest {

    private static final Instant MOMENT = Instant.parse("2026-06-01T03:00:00Z");
    private static final LocalDate BIRTH = LocalDate.parse("2008-06-01");

    @Test
    void majorityDependsOnTheTimeZoneOfTheClinic() {
        ClinicCalendar guayaquil = calendarAt(ZoneId.of("America/Guayaquil"));
        ClinicCalendar utc = calendarAt(ZoneId.of("UTC"));

        // 22:00 of May 31 in Guayaquil: still a minor there, already an adult in UTC.
        assertThat(AdulthoodTransition.reachedMajority(BIRTH, guayaquil.today())).isFalse();
        assertThat(AdulthoodTransition.reachedMajority(BIRTH, utc.today())).isTrue();
        assertThat(AdulthoodTransition.adultSince(BIRTH)).isEqualTo(LocalDate.parse("2026-06-01"));
    }

    @Test
    void theCandidateWindowCoversEveryPatientThatReachedMajority() {
        LocalDate leapBirth = LocalDate.parse("2008-02-29");
        LocalDate today = LocalDate.parse("2026-02-28");

        boolean adult = AdulthoodTransition.reachedMajority(leapBirth, today);

        assertThat(adult).isEqualTo(!Patient.isMinor(leapBirth, today));
        assertThat(!leapBirth.isAfter(AdulthoodTransition.candidateCutoff(today))).isTrue();
    }

    private static ClinicCalendar calendarAt(ZoneId zone) {
        return new ClinicCalendar() {
            @Override
            public ZoneId timeZone() {
                return zone;
            }

            @Override
            public LocalDate today() {
                return LocalDate.ofInstant(MOMENT, zone);
            }
        };
    }
}
