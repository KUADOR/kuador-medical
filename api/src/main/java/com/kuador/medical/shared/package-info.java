/**
 * Cross-cutting infrastructure: tenant context, access annotations and interceptor,
 * Problem Details and the capability check point. Open module: every module may use it,
 * and it must not depend on any business module.
 */
@ApplicationModule(displayName = "Shared infrastructure", type = ApplicationModule.Type.OPEN)
package com.kuador.medical.shared;

import org.springframework.modulith.ApplicationModule;
