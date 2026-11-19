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
- Base path `/api/v1` is added to every controller of `com.kuador.medical`; contract in `../contracts/identity.openapi.yaml`.

## Profiles

`local` (compose database, values from `.env`), `test` (Testcontainers, set by the build), `prod` (everything from the environment). Variables: `docs/deployment.md`.
