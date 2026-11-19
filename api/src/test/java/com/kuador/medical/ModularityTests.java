package com.kuador.medical;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModule;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Module boundaries (ADR-001, ADR-009): a module may only use the public API of another one.
 */
class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(MedicalApplication.class);

    @Test
    void moduleBoundariesAreRespected() {
        MODULES.verify();
    }

    @Test
    void skeletonModulesExist() {
        assertThat(MODULES.stream().map(ApplicationModule::getIdentifier).map(Object::toString))
                .contains("platform", "identity", "audit", "shared");
    }
}
