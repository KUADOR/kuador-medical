package com.kuador.medical.platform.access;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kuador.medical.platform.api.AccessMode;
import com.kuador.medical.platform.api.PlanCapabilities;
import com.kuador.medical.platform.plan.LimitCode;
import com.kuador.medical.platform.plan.ModuleCode;
import com.kuador.medical.shared.security.AllowedWhenReadOnly;
import com.kuador.medical.shared.security.PublicEndpoint;
import com.kuador.medical.shared.security.RequiresModule;
import com.kuador.medical.shared.tenancy.TenantContext;
import com.kuador.medical.shared.web.ApiException;
import com.kuador.medical.shared.web.ErrorCode;
import java.util.EnumSet;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

/** {@link TenantWriteGuard}: access mode, modules of the plan and the read-only exemptions (ADR-017). */
class TenantWriteGuardTest {

    private static final UUID CLINIC_A = UUID.randomUUID();

    private final FakeCapabilities capabilities = new FakeCapabilities();
    private final TenantWriteGuard guard = new TenantWriteGuard(capabilities);

    @Test
    void aFullClinicReadsAndWrites() {
        capabilities.mode = AccessMode.FULL;

        assertThat(asClinic("GET", "read")).isTrue();
        assertThat(asClinic("POST", "write")).isTrue();
        assertThat(asClinic("DELETE", "write")).isTrue();
    }

    @Test
    void aReadOnlyOrRetainedClinicReadsButDoesNotWrite() {
        for (AccessMode mode : EnumSet.of(AccessMode.READ_ONLY, AccessMode.RETENTION)) {
            capabilities.mode = mode;

            assertThat(asClinic("GET", "read")).as(mode + " reads").isTrue();
            for (String method : Set.of("POST", "PUT", "PATCH", "DELETE")) {
                assertCode(() -> asClinic(method, "write"), ErrorCode.TENANT_READ_ONLY);
            }
            assertThat(asClinic("POST", "exempted")).as("@AllowedWhenReadOnly").isTrue();
            assertThat(asClinic("POST", "open")).as("public handlers are not bound to a clinic session").isTrue();
        }
    }

    @Test
    void aPurgedClinicHasNoAccess() {
        capabilities.mode = AccessMode.NONE;

        assertCode(() -> asClinic("GET", "read"), ErrorCode.FORBIDDEN);
        assertCode(() -> asClinic("POST", "exempted"), ErrorCode.FORBIDDEN);
    }

    @Test
    void aModuleOutsideThePlanIsRejectedEvenForReads() {
        capabilities.mode = AccessMode.FULL;
        capabilities.modules = EnumSet.of(ModuleCode.USERS);

        assertCode(() -> asClinic("GET", "patients"), ErrorCode.MODULE_NOT_IN_PLAN);
        assertCode(() -> asClinic("POST", "patients"), ErrorCode.MODULE_NOT_IN_PLAN);

        capabilities.modules = EnumSet.of(ModuleCode.PATIENTS);
        assertThat(asClinic("GET", "patients")).isTrue();
    }

    @Test
    void platformSessionsAndAnonymousRequestsAreNotChecked() throws Exception {
        capabilities.mode = AccessMode.NONE;

        assertThat(guard.preHandle(request("POST"), new MockHttpServletResponse(), handler("write"))).isTrue();
        assertThat(capabilities.calls).isZero();
    }

    private boolean asClinic(String httpMethod, String handler) {
        return TenantContext.callAs(CLINIC_A, () -> {
            try {
                return guard.preHandle(request(httpMethod), new MockHttpServletResponse(), handler(handler));
            } catch (ApiException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(code));
    }

    private static MockHttpServletRequest request(String method) {
        return new MockHttpServletRequest(method, "/api/v1/fixture");
    }

    private static HandlerMethod handler(String name) {
        try {
            Object bean = "patients".equals(name) ? new PatientsFixture() : new Fixture();
            return new HandlerMethod(bean, bean.getClass().getDeclaredMethod(name));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    static class Fixture {
        void read() {}

        void write() {}

        @AllowedWhenReadOnly
        void exempted() {}

        @PublicEndpoint
        void open() {}
    }

    @RequiresModule("PATIENTS")
    static class PatientsFixture {
        void patients() {}
    }

    private static final class FakeCapabilities implements PlanCapabilities {

        AccessMode mode = AccessMode.FULL;
        Set<ModuleCode> modules = EnumSet.allOf(ModuleCode.class);
        int calls;

        @Override
        public boolean isModuleEnabled(UUID tenantId, ModuleCode module) {
            assertThat(tenantId).isEqualTo(CLINIC_A);
            return modules.contains(module);
        }

        @Override
        public OptionalLong limit(UUID tenantId, LimitCode limit) {
            return OptionalLong.empty();
        }

        @Override
        public void assertCanCreate(UUID tenantId, LimitCode limit) {}

        @Override
        public AccessMode accessMode(UUID tenantId) {
            calls++;
            assertThat(tenantId).isEqualTo(CLINIC_A);
            return mode;
        }
    }
}
