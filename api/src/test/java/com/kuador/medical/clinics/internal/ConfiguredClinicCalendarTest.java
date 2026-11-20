package com.kuador.medical.clinics.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ConfiguredClinicCalendarTest {

    @Test
    void blankTimeZoneFallsBackToUtc() {
        var calendar = new ConfiguredClinicCalendar(new ConfiguredClinicCalendar.Properties(""));

        assertThat(calendar.timeZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void configuredTimeZoneIsUsed() {
        var calendar = new ConfiguredClinicCalendar(new ConfiguredClinicCalendar.Properties(" America/Guayaquil "));

        assertThat(calendar.timeZone()).isEqualTo(ZoneId.of("America/Guayaquil"));
    }
}
