package com.kuador.medical;

import com.kuador.medical.identity.PlanLimits;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL for integration tests. One container per test JVM, reused by every cached context.
 * Emails are captured in memory ({@link CapturingNotificationSender}), never sent, and plan limits
 * are set per test clinic ({@link AdjustablePlanLimits}).
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18-alpine"));
    }

    @Bean
    @Primary
    CapturingNotificationSender capturingNotificationSender() {
        return new CapturingNotificationSender();
    }

    @Bean
    @Primary
    AdjustablePlanLimits adjustablePlanLimits(@Qualifier("subscriptionCapabilities") PlanLimits plan) {
        return new AdjustablePlanLimits(plan);
    }
}
