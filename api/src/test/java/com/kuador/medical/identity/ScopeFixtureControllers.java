package com.kuador.medical.identity;

import com.kuador.medical.shared.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test-only endpoints standing for a clinic operation and a platform operation, so the scope
 * rules can be proven before the role and catalog endpoints exist.
 */
final class ScopeFixtureControllers {

    private ScopeFixtureControllers() {}

    @RestController
    static class ClinicEndpoint {

        @RequiresPermission("roles.read")
        @GetMapping("/test/clinic-operation")
        String clinicOperation() {
            return "clinic";
        }
    }

    @RestController
    static class PlatformEndpoint {

        @RequiresPermission("platform.permissions.read")
        @GetMapping("/test/platform-operation")
        String platformOperation() {
            return "platform";
        }
    }
}
