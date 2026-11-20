package com.kuador.medical.local;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.TestcontainersConfiguration;
import db.localseed.LocalSeedMigration;
import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Without the "local" profile the seed is not loaded (MEDK-26 plan, decision 3): Flyway does not
 * look at its location and neither the seed clinics nor their users exist.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class DefaultProfileWithoutSeedIT {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void flywayDoesNotScanTheSeedLocation() {
        assertThat(Arrays.stream(flyway.getConfiguration().getLocations()).map(Object::toString))
                .noneMatch(location -> location.contains(LocalSeedMigration.LOCATION_PATH));
    }

    @Test
    void noSeedMigrationWasApplied() {
        assertThat(Arrays.stream(flyway.info().applied()).map(info -> info.getDescription()))
                .doesNotContain(LocalSeedMigration.DESCRIPTION);
    }

    @Test
    void seedClinicsAndUsersDoNotExist() {
        assertThat(jdbc.queryForObject("select count(*) from tenant where id in (?, ?)", Integer.class,
                LocalSeedMigration.TENANT_A, LocalSeedMigration.TENANT_B)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from identity_user_account where email_normalized in (?, ?)",
                Integer.class, LocalSeedMigration.USER_A_EMAIL, LocalSeedMigration.USER_B_EMAIL)).isZero();
    }
}
