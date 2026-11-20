package com.kuador.medical.clinics.internal;

import com.kuador.medical.clinics.ClinicCalendar;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
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
        String timeZone = properties.timeZone();
        // An empty KUADOR_CLINIC_TIME_ZONE (as in .env.example) means "use the default".
        this.zone = timeZone == null || timeZone.isBlank() ? ZoneOffset.UTC : ZoneId.of(timeZone.trim());
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
