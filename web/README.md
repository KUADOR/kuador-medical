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
    auth/       … plus AuthService (login, signup, password change, logout) and navigation
                (menu by permission, landing page per account, safe return URLs)
    errors/     ERROR_MESSAGES: every contract error `code` → Spanish message
    forms/      Spanish validation messages, server field errors on controls, focus helpers
    notice/     One-shot confirmation message carried across a navigation
    roles/      RolesService (/roles, /permissions) and Spanish permission labels
    platform/   PlatformService (/platform/permissions)
    system/     SystemInfoService (GET /system/info)
  features/
    status/           System status page: example web → API → database flow
    signup/           Clinic self-signup (creator becomes OWNER and ADMIN), then automatic login
    login/            Login; follows ?returnUrl= only for in-app paths
    change-password/  Mandatory password change; the only usable page while it is pending
    roles/            Role list (delete with confirmation) and role form (create, edit, read-only view)
    platform/         Full permission catalog for the SUPER_ADMIN
    message/          Informative page (forbidden, not found)
src/testing/    Test-only fixtures and app harness (real routes, guards and interceptor)
```

## Auth building blocks

- `authInterceptor` adds `Authorization: Bearer …` only to requests under `API_BASE_URL`. A 401 ends the session
  and goes to `/login`; a 403 `PASSWORD_CHANGE_REQUIRED` goes to `/change-password`.
- `requirePermissions(...codes)` guards a route: no session → `/login?returnUrl=…`, pending password change →
  `/change-password`, missing permission → `/forbidden`. `authenticatedGuard` only needs a session.
- `pendingPasswordChangeGuard` opens `/change-password` only while the change is pending.
- The API enforces every permission (ADR-008); guards only shape navigation.

## Pages and permissions

| Route | Guard | Notes |
|---|---|---|
| `/` | `homeGuard` | Visitors → `/status`; accounts → their landing page (pending change → `/change-password`, then first menu entry). |
| `/signup`, `/login` | `guestGuard` | Only without a session. |
| `/change-password` | `pendingPasswordChangeGuard` | Only while `mustChangePassword` is true. |
| `/roles` | `roles.read` | Create / edit / delete buttons follow `roles.create`, `roles.update`, `roles.delete`. |
| `/roles/new` | `roles.read` + `roles.create` | |
| `/roles/:id` | `roles.read` | Read-only for system roles (OWNER, ADMIN) or without `roles.update`. |
| `/platform/permissions` | `platform.permissions.read` | SUPER_ADMIN only; clinic accounts get `/forbidden`. |

The header menu is built from the permissions of `GET /me` and is empty while a password change is pending.

## Trying the flow against the mock

`npm run dev:mock` and open `http://localhost:4200/signup`. Prism answers with the contract examples, so the
signup logs in as the example owner, `POST /roles` returns the example role and `/roles/{id}` shows OWNER
(read-only). Other branches can be forced with the `Prefer` header (e.g. `Prefer: code=409` on
`/tenants/signup` for `EMAIL_TAKEN`, `Prefer: example=superAdmin` on `/me` for the SUPER_ADMIN flow).

## Errors

Use `errorMessage(error)` to show any HTTP error to people: it maps the Problem Details `code` through
`ERROR_MESSAGES` (typed as `Record<ErrorCode, string>`, so a new contract code breaks the build until mapped),
handles network failures and never shows server details. `fieldErrorMessage()` covers `errors[].code`.
