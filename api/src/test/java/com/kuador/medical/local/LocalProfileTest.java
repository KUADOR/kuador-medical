package com.kuador.medical.local;

import com.kuador.medical.TestcontainersConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Runs the application with the "local" profile on top of "test": the local seed is applied to the
 * Testcontainers database and Actuator is served as in local. The variables the local profile
 * requires are given as test properties; the values are random, never real credentials. Every
 * test class using this annotation shares one context (and one database).
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest(properties = {
    "DB_URL=jdbc:postgresql://overridden-by-testcontainers/medical",
    "DB_USERNAME=test",
    "DB_PASSWORD=test",
    "KUADOR_JWT_SECRET=${random.uuid}${random.uuid}",
    "KUADOR_TOTP_ENCRYPTION_KEY=${random.uuid}${random.uuid}",
    "LOCAL_SEED_PASSWORD=" + LocalProfileTest.SEED_PASSWORD
})
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
@Import(TestcontainersConfiguration.class)
public @interface LocalProfileTest {

    /** Password of the seed users in tests only; locally it comes from LOCAL_SEED_PASSWORD. */
    String SEED_PASSWORD = "local-seed-test-passphrase";
}
