package com.kuador.medical.shared.capability;

import static org.assertj.core.api.Assertions.assertThat;

import com.kuador.medical.platform.SystemInfoController;
import org.junit.jupiter.api.Test;

/**
 * Constitution VII exception: until plans exist, only the core capabilities are enabled
 * and any handler outside a core module is rejected.
 */
class CoreCapabilityCheckerTest {

    private final CoreCapabilityChecker checker = new CoreCapabilityChecker();
    private final ModuleCapabilityResolver resolver = new ModuleCapabilityResolver();

    @Test
    void coreCapabilitiesAreEnabled() {
        assertThat(checker.isEnabled(Capability.PLATFORM)).isTrue();
        assertThat(checker.isEnabled(Capability.IDENTITY)).isTrue();
        assertThat(checker.isEnabled(Capability.PATIENTS)).isTrue();
    }

    @Test
    void missingCapabilityIsDisabled() {
        assertThat(checker.isEnabled(null)).isFalse();
    }

    @Test
    void capabilityIsResolvedFromTheModuleOfTheHandler() {
        assertThat(resolver.resolve(SystemInfoController.class)).contains(Capability.PLATFORM);
        assertThat(resolver.resolve(CoreCapabilityCheckerTest.class)).contains(Capability.PLATFORM);
        assertThat(resolver.resolvePackage("com.kuador.medical.identity.role")).contains(Capability.IDENTITY);
        assertThat(resolver.resolvePackage("com.kuador.medical.audit")).contains(Capability.PLATFORM);
        assertThat(resolver.resolvePackage("com.kuador.medical.patients.registration")).contains(Capability.PATIENTS);
    }

    @Test
    void handlerOutsideACoreModuleHasNoCapability() {
        assertThat(resolver.resolvePackage("com.kuador.medical.pharmacy")).isEmpty();
        assertThat(resolver.resolvePackage("com.kuador.medicalx")).isEmpty();
        assertThat(resolver.resolve(String.class)).isEmpty();
    }
}
