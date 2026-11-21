# Medical Kuador · api

Pieza **api** del proyecto MEDK. Stack: Java 25 · Spring Boot 4 · PostgreSQL.

| Compilar | Probar | Levantar |
|---|---|---|
| `./mvnw -B package` | `./mvnw -B verify` | `./mvnw spring-boot:run` |

`./mvnw -B verify` needs JDK 25 and a running Docker (integration tests start PostgreSQL with Testcontainers). Unit tests are `*Test`/`*Tests` (surefire); integration tests are `*IT` (failsafe).

## Modules (`com.kuador.medical`)

| Module | Content |
|---|---|
| `platform` | Tenants, clinic self-signup, `GET /api/v1/system/info` |
| `identity` | Accounts, memberships, authentication, permission catalog, clinic roles |
| `audit` | Security events without personal data |
| `shared` | Open infrastructure module: tenancy, access annotations, Problem Details, capabilities |

Boundaries are verified by `ModularityTests` (Spring Modulith, ADR-009).

## Rules every endpoint follows

- **Access declared, denied by default (ADR-008).** Each handler (or its controller) declares exactly one of `@PublicEndpoint`, `@AuthenticatedEndpoint` or `@RequiresPermission("<code>")`. `AccessControlInterceptor` answers 403 `FORBIDDEN` to any other handler and `ArchitectureTests` fails the build. Callers are resolved through `AccessChecker`; until identity provides one, `DenyAllAccessChecker` treats everyone as anonymous (401 `UNAUTHENTICATED`).
- **Capabilities (ADR-004).** Every handler passes through `CapabilityChecker`; its capability comes from its module (`ModuleCapabilityResolver`). `CoreCapabilityChecker` enables only `PLATFORM` and `IDENTITY` until plans exist. A new module must be mapped or its endpoints are rejected.
- **Tenant from the backend only (ADR-007).** Tenant-scoped entities declare Hibernate `@TenantId`; `TenantIdentifierResolver` reads `TenantContext`, bound only by the backend. Without a tenant, queries see nothing and inserts fail. Native SQL in business modules needs an explicit tenant filter and an isolation test.
- **Errors.** Problem Details (RFC 9457, `application/problem+json`) with a stable `code` from `ErrorCode` (mirrors the contract). Throw `ApiException` for domain errors; internal messages are never returned.
- **Authentication (ADR-006, ADR-010).** `POST /auth/login` returns an HS256 JWT (`sub`, `scope` = `TENANT`|`PLATFORM`, `tid` for clinics). `BearerTokenAuthenticationFilter` verifies it, binds the clinic of a `TENANT` token in `TenantContext` and resolves the caller from the database on every request (`CurrentUser`, `TokenAccessChecker`): role or password changes apply to the next request. A `PLATFORM` account only holds platform permissions and a clinic account only clinic ones, so each gets 403 on the other's endpoints.
- **Mandatory password change.** While `mustChangePassword` is set, non-public handlers answer 403 `PASSWORD_CHANGE_REQUIRED` unless marked `@AllowedWhilePasswordChangeRequired` (only `POST /auth/password` and `GET /me`). Public handlers ignore the caller.
- **Initial SUPER_ADMIN.** `BootstrapAdminInitializer` creates it at startup from `KUADOR_BOOTSTRAP_ADMIN_EMAIL`/`KUADOR_BOOTSTRAP_ADMIN_PASSWORD` only when no platform admin exists; it never promotes an existing clinic account and never logs those values.
- **Signup.** `POST /tenants/signup` (platform) binds the new tenant, then creates it and calls `identity.TenantOwnerProvisioning` in one transaction (account, membership, OWNER and ADMIN with every clinic permission). Identity reads clinics through the `TenantDirectory` port that platform implements, so modules do not form a cycle.
- **Roles and permission catalog.** `Permission` is the fixed catalog (no table). `GET /permissions` lists the clinic-scoped codes (`roles.read`) and `GET /platform/permissions` the whole catalog (`platform.permissions.read`, PLATFORM only). `RoleService` runs the `/roles` CRUD in the tenant of the token: names are unique per clinic ignoring case (`ROLE_NAME_TAKEN`), codes must be clinic-scoped (`AssignablePermissions`, 400 `VALIDATION` with `UNKNOWN_PERMISSION`/`DUPLICATE` per `permissions[i]`), OWNER and ADMIN are immutable (`SYSTEM_ROLE_IMMUTABLE`), assigned roles cannot be deleted (`ROLE_IN_USE`), a stale `version` is `VERSION_CONFLICT`, and roles of another clinic are 404. Database constraints settle races the same way. Every change is audited (`ROLE_CREATED`/`UPDATED`/`DELETED`, target `ROLE`).
- Base path `/api/v1` is added to every controller of `com.kuador.medical`; contract in `../contracts/identity.openapi.yaml`.

## Profiles

`local` (compose database, values from the environment or `api/.env`, copied from `api/.env.example`), `test` (Testcontainers, set by the build), `prod` (everything from the environment). Variables: `docs/deployment.md`.

### Local profile (MEDK-29)

- **Fail fast.** `shared.config.LocalEnvironmentValidator` stops the start-up when `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `KUADOR_JWT_SECRET`, `KUADOR_TOTP_ENCRYPTION_KEY` or `LOCAL_SEED_PASSWORD` is missing or blank, naming the variables and never their values.
- **Seed.** Only `local` adds the Flyway location `classpath:db/localseed`: `db.localseed.LocalSeedMigration` (repeatable, idempotent) creates the fictitious clinics A and B, each with one branch, OWNER/ADMIN and one OWNER test user whose password is `LOCAL_SEED_PASSWORD` (stored as an Argon2id hash). Ids and emails are constants of that class.
- **Listens on** `SERVER_ADDRESS:SERVER_PORT` (default `0.0.0.0:8080`) so a physical device on the LAN can reach it.
- **Actuator** exposes only `GET /actuator/health` (public, components without details) in every profile.
- **Public endpoints** are an explicit list, `shared.security.PublicEndpoints`; `EndpointPermissionTest` fails when a `@PublicEndpoint` handler is not on it.
- Tests: `LocalConfigFailFastTest`, `EndpointPermissionTest`, `LocalSeedLocationTest`, `LocalProfileIT`, `LocalSeedTenantIsolationIT` (A never reads or writes B, even sending ids of B) and `DefaultProfileWithoutSeedIT`.
