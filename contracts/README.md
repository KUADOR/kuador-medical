# Contracts

HTTP contracts of Medical Kuador, written contract-first (ADR-011) in OpenAPI 3.1.

| File | Scope | State |
|---|---|---|
| `identity.openapi.yaml` | System status, clinic self-signup, password change, permission catalog, clinic roles and platform permissions. | Implemented by the api. |
| `user-registration.openapi.yaml` | User registration epic (MEDK-12): multi-clinic login, branch selection and switching, refresh tokens, password reset, invitations, email verification, `/me`, staff users and branch role assignments, roles, patients, consent, guardians, adult patient links and patient self-registration. | Target contract; implemented by MEDK-14 to MEDK-18. |

## Overlapping operations

`GET /permissions` and `/roles` appear in both files. The version in `identity.openapi.yaml`
is the one the api serves today; the version in `user-registration.openapi.yaml` is the
target. `POST /auth/login` and `GET /me` are served from `user-registration.openapi.yaml`
since MEDK-15. The task that implements an operation of
`user-registration.openapi.yaml` removes its old version from `identity.openapi.yaml` in the
same pull request, together with the web and mobile clients that use it, so each operation
has a single source of truth once implemented.

## Conventions

- Base path `/api/v1`. No request carries a tenant identifier: the backend resolves the clinic and the active branch from the access token (ADR-007, ADR-013).
- Every operation declares its required permission in `x-required-permission` (`public`, `authenticated` or a permission code) (ADR-008). Public operations are also marked `x-public: true` and declare `security: []`.
- Permission codes are those of the fixed catalog (`Permission` in the api, ADR-003) and are listed in the `PermissionCode` enum of `user-registration.openapi.yaml`.
- Creation requests (`POST` answering `201`, and the public self-registration) accept an optional `Idempotency-Key` header and an optional client-generated `id` (UUID).
- One-time tokens (invitation, password reset, email verification) travel only in request bodies, never in paths or query strings (ADR-014).
- Errors are Problem Details (RFC 9457, `application/problem+json`) with a stable `code` from the `ErrorCode` enum, mirrored by `ErrorCode` in the api. Clients map each `code` to a user-facing message in Spanish.
- Descriptions, summaries and examples are in English; examples use fictitious data only.

`UserRegistrationContractTest` (api, part of `./mvnw -B verify`) fails the build if an
operation of `user-registration.openapi.yaml` breaks these rules, if a planned operation is
missing, or if `ErrorCode` or `PermissionCode` drift from the code.

## Permission catalog (MEDK-12)

| Code | Use |
|---|---|
| `roles.read` | View roles and the assignable permission catalog |
| `roles.create` / `roles.update` / `roles.delete` | Create, update and delete non-protected roles |
| `identity.users.read` | View staff users |
| `identity.users.manage` | Register, invite, edit, disable and enable staff users |
| `identity.assignments.manage` | Assign roles per branch |
| `patients.read` | View patients, guardian links and self-registration settings |
| `patients.register` | Register patients, record consent and invite adult patients to their account |
| `patients.edit` | Edit patient data |
| `patients.guardians.manage` | Manage guardians and their links |
| `patients.selfRegistration.configure` | Enable or disable patient self-registration |
| `platform.permissions.read` | Platform only: view the whole catalog |

The plan of MEDK-12 named the role permissions `identity.roles.read` and
`identity.roles.manage`; the already published `roles.*` codes are kept instead, so stored
roles and clients do not break.

## Lint

```bash
npx @redocly/cli lint
```

Configuration lives in `redocly.yaml` at the repository root; justified exceptions in `.redocly.lint-ignore.yaml`.

## Mock

```bash
npx @stoplight/prism-cli mock contracts/identity.openapi.yaml -p 4010
npx @stoplight/prism-cli mock contracts/user-registration.openapi.yaml -p 4011
```

Prism serves the paths without the `/api/v1` prefix (for example `GET http://localhost:4010/roles`); a client proxy should strip it. Use the `Prefer` header to pick a response, for example `Prefer: code=409, example=systemRole` or, on `POST /auth/login`, `Prefer: example=clinicSelection`. Recent Prism versions require Node 24 or later; version 5 runs on Node 22.
