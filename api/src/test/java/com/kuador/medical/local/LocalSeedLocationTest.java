package com.kuador.medical.local;

import static org.assertj.core.api.Assertions.assertThat;

import db.localseed.LocalSeedMigration;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Only the "local" profile adds the seed location to Flyway (MEDK-26 plan, decision 3), and its
 * configuration only references environment variables: no secret is written in the file.
 */
class LocalSeedLocationTest {

    private static final Path RESOURCES = Path.of("src", "main", "resources");

    @Test
    void onlyTheLocalProfileReferencesTheSeedLocation() throws IOException {
        assertThat(read("application.yaml")).doesNotContain(LocalSeedMigration.LOCATION_PATH);
        assertThat(read("application-prod.yaml")).doesNotContain(LocalSeedMigration.LOCATION_PATH);
        assertThat(read("application-local.yaml")).contains("classpath:" + LocalSeedMigration.LOCATION_PATH);
    }

    @Test
    void theSeedMigrationLivesInTheSeedLocation() {
        assertThat(LocalSeedMigration.class.getPackageName().replace('.', '/'))
                .isEqualTo(LocalSeedMigration.LOCATION_PATH);
    }

    @Test
    void localProfileTakesSecretsOnlyFromVariablesWithoutDefaults() throws IOException {
        String local = read("application-local.yaml");
        for (String variable : List.of("DB_URL", "DB_USERNAME", "DB_PASSWORD", "KUADOR_JWT_SECRET", "LOCAL_SEED_PASSWORD")) {
            assertThat(local).as(variable).contains("${" + variable + "}");
        }
        assertThat(local).contains("${SERVER_PORT:8080}", "${SERVER_ADDRESS:0.0.0.0}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void actuatorExposesOnlyHealthInEveryProfile() throws IOException {
        Map<String, Object> common = new Yaml().load(read("application.yaml"));
        Map<String, Object> management = (Map<String, Object>) common.get("management");
        Map<String, Object> endpoints = (Map<String, Object>) management.get("endpoints");
        Map<String, Object> web = (Map<String, Object>) endpoints.get("web");
        Map<String, Object> exposure = (Map<String, Object>) web.get("exposure");
        assertThat(exposure).containsEntry("include", "health");
        for (String profile : List.of("application-local.yaml", "application-prod.yaml")) {
            assertThat(read(profile)).as(profile).doesNotContain("exposure");
        }
    }

    private static String read(String file) throws IOException {
        return Files.readString(RESOURCES.resolve(file));
    }
}
