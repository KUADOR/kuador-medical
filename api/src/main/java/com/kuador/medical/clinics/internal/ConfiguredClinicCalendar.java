package com.kuador.medical.clinics.internal;

import com.kuador.medical.clinics.ClinicCalendar;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.stereotype.Component;

/**
 * {@link ClinicCalendar} with one configured time zone for every clinic (plan of MEDK-12,
 * precondition: minimal API of the owner module). [POR DEFINIR: zona horaria por clínica].
 */
@Component
@EnableConfigurationProperties(ConfiguredClinicCalendar.Properties.class)
class ConfiguredClinicCalendar implements ClinicCalendar {

    private final ZoneId zone;
    private final Clock clock = Clock.systemUTC();

    ConfiguredClinicCalendar(Properties properties) {
        this.zone = ZoneId.of(properties.timeZone());
    }

    @Override
    public ZoneId timeZone() {
        return zone;
    }

    @Override
    public LocalDate today() {
        return LocalDate.now(clock.withZone(zone));
    }

    /** {@code kuador.clinics}: time zone of the clinics, UTC by default. */
    @ConfigurationProperties("kuador.clinics")
    record Properties(@DefaultValue("UTC") String timeZone) {}
}
