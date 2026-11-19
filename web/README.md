# Medical Kuador · web

Angular (standalone components, signals, zoneless) client of the Medical Kuador API.
It is built contract-first (ADR-011): types come from `contracts/identity.openapi.yaml` and, while the
API is not ready, it runs against a Prism mock of that contract.

| Build | Test | Run |
|---|---|---|
| `npm run build` | `npm test -- --watch=false` | `npm start` |

Requires Node.js `^22.22.3` or `^24.15.0`.

## Scripts

| Script | What it does |
|---|---|
| `npm start` | Dev server on `http://localhost:4200`, proxying `/api` to the real API on `http://localhost:8080` (`proxy.conf.json`). |
| `npm run mock` | Prism mock of `../contracts/identity.openapi.yaml` on `http://localhost:4010`. |
| `npm run start:mock` | Dev server proxying `/api/v1` to the mock, stripping the prefix (`proxy.mock.conf.json`). |
| `npm run dev:mock` | `mock` and `start:mock` together. |
| `npm run api:generate` | Regenerates `src/app/core/api/identity.schema.d.ts` from the contract. |
| `npm run api:check` | Fails if the generated types are out of date with the contract. |

Pick a mock response with the `Prefer` header, e.g. `curl -H 'Prefer: code=500' http://localhost:4200/api/v1/system/info`.
Prism is pinned to 5.14.2, the last version that runs on Node 22.

## Runtime configuration

`public/config.json` is read at startup (before any request), so one build serves every environment:

```json
{ "API_BASE_URL": "/api/v1" }
```

The default is same-origin: the dev server proxy and the nginx image (`nginx.conf`) forward `/api/` to the API.
In the container, setting the `API_BASE_URL` environment variable rewrites `config.json` at start
(`docker-entrypoint.d/40-runtime-config.sh`). A cross-origin URL also needs that origin in the API's
`KUADOR_CORS_ALLOWED_ORIGINS`.

## Structure

```
src/app/
  core/
    api/        identity.schema.d.ts (generated, do not edit) and api-types.ts (aliases, contract paths)
    config/     AppConfigService: loads config.json, builds API URLs
    auth/       SessionStore (token in sessionStorage + GET /me), authInterceptor, guards, auth routes
    errors/     ERROR_MESSAGES: every contract error `code` → Spanish message
    system/     SystemInfoService (GET /system/info)
  features/
    status/     System status page: example web → API → database flow
    message/    Informative page (forbidden, not found, placeholders for MEDK-8)
src/testing/    Test-only fixtures
```

## Auth building blocks

- `authInterceptor` adds `Authorization: Bearer …` only to requests under `API_BASE_URL`. A 401 ends the session
  and goes to `/login`; a 403 `PASSWORD_CHANGE_REQUIRED` goes to `/change-password`.
- `requirePermissions(...codes)` guards a route: no session → `/login?returnUrl=…`, pending password change →
  `/change-password`, missing permission → `/forbidden`. `authenticatedGuard` only needs a session.
- `pendingPasswordChangeGuard` opens `/change-password` only while the change is pending.
- The API enforces every permission (ADR-008); guards only shape navigation.
- `/login` and `/change-password` are placeholders until MEDK-8 adds the real pages.

## Errors

Use `errorMessage(error)` to show any HTTP error to people: it maps the Problem Details `code` through
`ERROR_MESSAGES` (typed as `Record<ErrorCode, string>`, so a new contract code breaks the build until mapped),
handles network failures and never shows server details. `fieldErrorMessage()` covers `errors[].code`.
