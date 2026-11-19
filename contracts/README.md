# Contracts

HTTP contracts of Medical Kuador, written contract-first (ADR-011) in OpenAPI 3.1.

| File | Scope |
|---|---|
| `identity.openapi.yaml` | System status, clinic self-signup, login, password change, current account, permission catalog, clinic roles and platform permissions. |

## Conventions

- Base path `/api/v1`. No request carries a tenant identifier: the backend resolves it from the access token (ADR-007).
- Every operation declares its required permission in `x-required-permission` (`public`, `authenticated` or a permission code) (ADR-008).
- Errors are Problem Details (RFC 9457, `application/problem+json`) with a stable `code` from the `ErrorCode` enum. Clients map each `code` to a user-facing message.
- Descriptions, summaries and examples are in English.

## Lint

```bash
npx @redocly/cli lint
```

Configuration lives in `redocly.yaml` at the repository root; justified exceptions in `.redocly.lint-ignore.yaml`.

## Mock

```bash
npx @stoplight/prism-cli mock contracts/identity.openapi.yaml -p 4010
```

Prism serves the paths without the `/api/v1` prefix (for example `GET http://localhost:4010/roles`); a client proxy should strip it. Use the `Prefer` header to pick a response, for example `Prefer: code=409, example=systemRole`. Recent Prism versions require Node 24 or later.
