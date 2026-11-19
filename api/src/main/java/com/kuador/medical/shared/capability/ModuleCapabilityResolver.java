package com.kuador.medical.shared.capability;

import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Resolves the capability of a handler from the module (direct sub-package of
 * {@code com.kuador.medical}) it belongs to. A module without a mapping has no capability and
 * is therefore rejected: new modules must be mapped explicitly.
 */
public final class ModuleCapabilityResolver implements Function<Class<?>, Optional<Capability>> {

    private static final String ROOT = "com.kuador.medical.";

    private static final Map<String, Capability> MODULES = Map.of(
            "platform", Capability.PLATFORM,
            "audit", Capability.PLATFORM,
            "shared", Capability.PLATFORM,
            "identity", Capability.IDENTITY);

    @Override
    public Optional<Capability> apply(Class<?> type) {
        return resolve(type);
    }

    public Optional<Capability> resolve(Class<?> type) {
        return resolvePackage(type.getPackageName());
    }

    public Optional<Capability> resolvePackage(String packageName) {
        if (!packageName.startsWith(ROOT)) {
            return Optional.empty();
        }
        String module = packageName.substring(ROOT.length()).split("\\.", 2)[0];
        return Optional.ofNullable(MODULES.get(module));
    }
}
