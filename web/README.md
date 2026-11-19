# Medical Kuador · web

Angular (standalone components, signals, zoneless) client of the Medical Kuador API.
It is built contract-first (ADR-011): types come from `contracts/identity.openapi.yaml` (status, clinic
signup, password change, roles, platform catalog) and `contracts/user-registration.openapi.yaml` (MEDK-12:
authentication and session) and, while the API is not ready, it runs against Prism mocks of both.

> Authentication and session already follow `user-registration.openapi.yaml` (MEDK-20): multi-clinic login,
> refresh tokens, `GET /me` with branches and plan modules. Until the api implements it (MEDK-14 to MEDK-16),
> log in with `npm run dev:mock`; `npm start` against the current api only serves the identity operations.

| Build | Test | Run |
|---|---|---|
| `npm run build` | `npm test -- --watch=false` | `npm start` |

Requires Node.js `^22.22.3` or `^24.15.0`.

## Scripts

| Script | What it does |
|---|---|
| `npm start` | Dev server on `http://localhost:4200`, proxying `/api` to the real API on `http://localhost:8080` (`proxy.conf.json`). |
| `npm run mock` | Prism mocks: `identity.openapi.yaml` on `http://localhost:4010` and `user-registration.openapi.yaml` on `http://localhost:4011`. |
| `npm run start:mock` | Dev server proxying `/api/v1` to the mocks, stripping the prefix (`proxy.mock.conf.json`): `/auth/*` (except `/auth/password`), `/me`, `/public/*`, `/patients` and `/patient-registration-settings` go to 4011, the rest to 4010. |
| `npm run dev:mock` | `mock` and `start:mock` together. |
| `npm run api:generate` | Regenerates `identity.schema.d.ts` and `user-registration.schema.d.ts` in `src/app/core/api/`. |
| `npm run api:check` | Fails if the generated types are out of date with the contracts. |

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
    api/        *.schema.d.ts (generated, do not edit) and api-types.ts (aliases, contract paths)
    catalogs/   Patient catalogs of the forms (document types, sex, consent version) [POR DEFINIR]
    config/     AppConfigService: loads config.json, builds API URLs
    session/    SessionStore (access + refresh tokens in sessionStorage, GET /me), TokenRefresher,
                authInterceptor, guards, auth routes, AuthService (login steps, branch switch, logout,
                password reset, invitations, email verification, self-registration, signup,
                password change) and navigation (menu by permission and plan module, landing page,
                safe return URLs)
    errors/     ERROR_MESSAGES: every contract error `code` → Spanish message
    forms/      Spanish validation messages, server field errors on controls, focus helpers
    notice/     One-shot confirmation message carried across a navigation
    roles/      RolesService (/roles, /permissions) and Spanish permission labels
    patients/   PatientsService (/patients, guardian links, consents, account invitation,
                /me/guardian-links, /patient-registration-settings; creations send Idempotency-Key)
                and Spanish labels, minor-age check and date formatting
    platform/   PlatformService (/platform/permissions)
    system/     SystemInfoService (GET /system/info)
  features/
    status/           System status page: example web → API → database flow
    signup/           Clinic self-signup (creator becomes OWNER and ADMIN), then automatic login
    auth/             Login (follows ?returnUrl= only for in-app paths), clinic and branch selection,
                      forgot / reset password, invitation, email verification, patient self-registration
                      and the header branch switcher
    change-password/  Mandatory password change; the only usable page while it is pending
    roles/            Role list (delete with confirmation) and role form (create, edit, read-only view)
    patients/         Patient list and search, registration (consent, legal guardian of minors) and
                      edit, detail (consent, account invitation, guardian links and history),
                      self-registration switch and public link, adult patient authorized contacts
    platform/         Full permission catalog for the SUPER_ADMIN
    message/          Informative page (forbidden, not found)
src/testing/    Test-only fixtures and app harness (real routes, guards and interceptor)
```

## Auth building blocks

- `authInterceptor` adds `Authorization: Bearer …` only to requests under `API_BASE_URL`, never to the public auth
  operations. An expired access token is refreshed first (`POST /auth/refresh`, one refresh at a time shared by
  concurrent requests); a 401 gets one refresh and one retry, and if that fails the session ends and goes to
  `/login?returnUrl=…`. A 403 `PASSWORD_CHANGE_REQUIRED` goes to `/change-password`.
- Tokens live in `sessionStorage` (cleared with the tab), never in `localStorage`. The pre-auth token of the
  clinic and branch selection stays in memory only.
- `requireAccess({ permissions, module })` guards a route: no session → `/login?returnUrl=…`, pending password
  change → `/change-password`, missing permission or plan module → `/forbidden`. `requirePermissions(...codes)`
  is the shorthand without module; `authenticatedGuard` only needs a session.
- Email links carry the one-time token in the fragment (`/invitation#t=…`, `/reset-password#t=…`,
  `/verify-email#t=…`, ADR-014). The page reads it and removes it from the address bar and the history entry.
- `pendingPasswordChangeGuard` opens `/change-password` only while the change is pending.
- The API enforces every permission (ADR-008); guards only shape navigation.

## Pages and permissions

| Route | Guard | Notes |
|---|---|---|
| `/` | `homeGuard` | Visitors → `/status`; accounts → their landing page (pending change → `/change-password`, then first menu entry). |
| `/signup`, `/login`, `/forgot-password` | `guestGuard` | Only without a session. |
| `/login/clinic`, `/login/branch` | `loginStepGuard` | Only while that login step is pending; a reload goes back to `/login`. |
| `/reset-password#t=…`, `/invitation#t=…`, `/verify-email#t=…` | — | Targets of the email links. |
| `/clinics/:publicCode/register` | `guestGuard` | Patient self-registration; 404 from the API means disabled. |
| `/change-password` | `pendingPasswordChangeGuard` | Only while `mustChangePassword` is true. |
| `/roles` | `roles.read` + module `identity` | Create / edit / delete buttons follow `roles.create`, `roles.update`, `roles.delete`. |
| `/roles/new` | `roles.read` + `roles.create` + module `identity` | |
| `/roles/:id` | `roles.read` + module `identity` | Read-only for system roles (OWNER, ADMIN) or without `roles.update`. |
| `/patients` | `patients.read` + module `patients` | "Registrar paciente" follows `patients.register`. |
| `/patients/new` | `patients.register` + module `patients` | Minors (by the browser date) require a legal guardian; the API decides with the clinic time zone. |
| `/patients/:id` | `patients.read` + module `patients` | Edit (`patients.edit`), consent and invitation (`patients.register`), guardian links (`patients.guardians.manage`). |
| `/patients/:id/edit` | `patients.read` + `patients.edit` + module `patients` | `PATCH` with only the changed fields and `version`. |
| `/patients/self-registration` | `patients.read` + module `patients` | Switch only with `patients.selfRegistration.configure`; public link while enabled. |
| `/my-contacts` | account kind `PATIENT` | Adult patient: link history and authorization of an emergency contact or delegated access. |
| `/platform/permissions` | `platform.permissions.read` | SUPER_ADMIN only; clinic accounts get `/forbidden`. |

The header menu is built from the permissions and `enabledModules` of `GET /me` and is empty while a password
change is pending. With more than one branch in `availableBranches`, the header shows a branch selector
(`POST /auth/switch-branch`, then `GET /me` again and the landing page).

## Trying the flow against the mock

`npm run dev:mock` and open `http://localhost:4200/login`: the mock logs in straight away as the example
reception account with two branches (the header shows the branch selector). `/invitation#t=one-time-example-token`
shows the example invitation. The clinic selection step can be seen with
`curl -H 'Prefer: example=clinicSelection' -X POST …/api/v1/auth/login`.

For the identity flows open `http://localhost:4200/signup`. Prism answers with the contract examples, so the
signup logs in as the example owner, `POST /roles` returns the example role and `/roles/{id}` shows OWNER
(read-only). Other branches can be forced with the `Prefer` header (e.g. `Prefer: code=409` on
`/tenants/signup` for `EMAIL_TAKEN`, `Prefer: example=superAdmin` on `/me` for the SUPER_ADMIN flow).

Patient pages: open `/patients` after logging in through the mock (the example reception account has the patient
permissions). Creations (`POST /patients`, `/consents`, `/guardian-links`, `/me/guardian-links`) send an
`Idempotency-Key` and a client `id`; both are kept while retrying after a network error and renewed once the
server answers.

## Errors

Use `errorMessage(error)` to show any HTTP error to people: it maps the Problem Details `code` through
`ERROR_MESSAGES` (typed as `Record<ErrorCode, string>`, so a new contract code breaks the build until mapped),
handles network failures and never shows server details. `fieldErrorMessage()` covers `errors[].code`.
