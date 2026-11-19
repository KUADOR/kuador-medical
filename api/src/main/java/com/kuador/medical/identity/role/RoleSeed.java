package com.kuador.medical.identity.role;

import com.kuador.medical.identity.Permission;
import com.kuador.medical.identity.PermissionScope;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Roles every new clinic receives, read from the versioned file {@code identity/role-seed.yaml}.
 * The file is validated when it is loaded: an unknown or platform permission, a repeated key or a
 * missing OWNER or ADMIN stops the application instead of seeding a wrong clinic.
 */
public record RoleSeed(int version, List<SeededRole> roles) {

    public static final String RESOURCE = "identity/role-seed.yaml";
    private static final String ALL_PERMISSIONS = "*";

    public RoleSeed {
        roles = List.copyOf(roles);
        Set<String> keys = new HashSet<>();
        Set<String> names = new HashSet<>();
        for (SeededRole role : roles) {
            if (!keys.add(role.key())) {
                throw new IllegalStateException("Repeated role key in the seed: " + role.key());
            }
            if (!names.add(role.name().toLowerCase())) {
                throw new IllegalStateException("Repeated role name in the seed: " + role.key());
            }
        }
        for (SystemRole systemRole : SystemRole.values()) {
            if (roles.stream().noneMatch(role -> role.system() == systemRole)) {
                throw new IllegalStateException("The seed has no " + systemRole + " role");
            }
        }
    }

    /** A role of the seed with its permissions resolved against the catalog. */
    public record SeededRole(
            String key,
            @Nullable SystemRole system,
            String name,
            @Nullable String description,
            boolean clinical,
            List<Permission> permissions) {

        public SeededRole {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(name, "name");
            permissions = List.copyOf(permissions);
        }
    }

    /** Loads the seed packaged with the application. */
    public static RoleSeed load() {
        try (InputStream in = RoleSeed.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing role seed " + RESOURCE);
            }
            return parse(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("unchecked")
    static RoleSeed parse(InputStream in) {
        Map<String, Object> document = new Yaml(new SafeConstructor(new LoaderOptions())).load(in);
        int version = ((Number) Objects.requireNonNull(document.get("version"), "version")).intValue();
        List<SeededRole> roles = new ArrayList<>();
        for (Map<String, Object> entry : (List<Map<String, Object>>) document.get("roles")) {
            Object system = entry.get("system");
            roles.add(new SeededRole(
                    (String) entry.get("key"),
                    system == null ? null : SystemRole.valueOf(system.toString()),
                    (String) entry.get("name"),
                    (String) entry.get("description"),
                    Boolean.TRUE.equals(entry.get("clinical")),
                    permissions((List<Object>) entry.getOrDefault("permissions", List.of()), (String) entry.get("key"))));
        }
        return new RoleSeed(version, roles);
    }

    private static List<Permission> permissions(List<Object> codes, String key) {
        List<Permission> catalog = Permission.ofScope(PermissionScope.TENANT);
        if (codes.contains(ALL_PERMISSIONS)) {
            return catalog;
        }
        Set<Permission> resolved = new HashSet<>();
        for (Object code : codes) {
            Permission permission = Permission.fromCode(code.toString())
                    .filter(found -> found.scope() == PermissionScope.TENANT)
                    .orElseThrow(() -> new IllegalStateException(
                            "Role " + key + " of the seed has a permission outside the clinic catalog: " + code));
            resolved.add(permission);
        }
        return catalog.stream().filter(resolved::contains).toList();
    }
}
