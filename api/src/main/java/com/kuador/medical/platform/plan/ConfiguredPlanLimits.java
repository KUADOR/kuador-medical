package com.kuador.medical.platform.plan;

import com.kuador.medical.identity.PlanLimits;
import java.util.OptionalInt;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Plan limits until the plans module exists (Constitution VII): plans and their limits are not
 * defined yet [POR DEFINIR: planes y límites que fija el SUPER_ADMIN], so no clinic has a user
 * limit unless {@code kuador.plans.user-limit} ({@code KUADOR_PLAN_USER_LIMIT}) sets one for every
 * clinic. Replace with the plan of each clinic when the plans module lands.
 */
@Component
@EnableConfigurationProperties(ConfiguredPlanLimits.Properties.class)
class ConfiguredPlanLimits implements PlanLimits {

    private final Properties properties;

    ConfiguredPlanLimits(Properties properties) {
        this.properties = properties;
    }

    @ConfigurationProperties("kuador.plans")
    record Properties(@Nullable Integer userLimit) {}

    @Override
    public OptionalInt userLimit(UUID tenantId) {
        Integer limit = properties.userLimit();
        return limit == null || limit <= 0 ? OptionalInt.empty() : OptionalInt.of(limit);
    }
}
