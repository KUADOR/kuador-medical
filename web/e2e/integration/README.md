# Web ↔ real api integration walk (MEDK-75)

`platform-lifecycle.integration.ts` drives the web in a real browser (Playwright) against the **real api**,
with no simulated API, through the whole clinic lifecycle of MEDK-49:

1. **Onboarding**: the SUPER_ADMIN registers a clinic with the trial plan (`/platform/tenants/new`).
2. **Invitation**: the OWNER opens the link of the email caught by Mailpit (`/owner-invitation#t=…`),
   sets the password and accepts the terms and the data policy.
3. **2FA**: the OWNER enrolls TOTP (code computed from the secret shown on screen), signs out and must
   verify the code on the next login.
4. **Suspension**: the SUPER_ADMIN upgrades the clinic to a paid plan, activates it and suspends it;
   the OWNER sees the read-only notice and can still export (JSON).
5. **Cancellation**: the clinic enters retention and shows the reactivation window.
6. **Reactivation**: manual, with a reason, back to Active.
7. **Purge**: second cancellation, retention ended in the disposable database, purge request and
   confirmation typing the RUC; the OWNER can no longer sign in.

Data is synthetic (`*.example.test` addresses, random RUC). Secrets come from the environment and are never
printed. It is opt-in: it is not part of `npm test` nor of CI.

## Run it

Local environment as in `docs/deployment.md` § «Entorno local» (db, Mailpit, api with the `local` profile and
`npm start`), with the SUPER_ADMIN initial password already changed (e.g. by `scripts/smoke-test.sh`). Then,
from `web/`:

```bash
set -a; . ../.env; set +a
E2E_WEB_URL=http://localhost:4200 \
E2E_API_URL=http://localhost:8080/api/v1 \
E2E_MAILPIT_URL=http://localhost:8025 \
E2E_SUPER_ADMIN_EMAIL="$KUADOR_BOOTSTRAP_ADMIN_EMAIL" \
E2E_SUPER_ADMIN_PASSWORD="$SMOKE_SUPER_ADMIN_NEW_PASSWORD" \
E2E_EXPIRE_RETENTION_COMMAND="docker compose -f ../compose.local.yaml exec -T db psql -U medical -d medical -c \"update subscription set retention_ends_at = now() - interval '1 minute' where tenant_id = '{tenantId}'\"" \
npm run test:integration
```

| Variable | Default | Meaning |
|---|---|---|
| `E2E_WEB_URL` | `http://localhost:4200` | Web dev server (its proxy sends `/api` to the api). |
| `E2E_API_URL` | `http://localhost:8080/api/v1` | api, used only to create the paid plan of the walk and to log in the SUPER_ADMIN. |
| `E2E_MAILPIT_URL` | `http://localhost:8025` | Mailpit UI/API, to read the OWNER invitation. |
| `E2E_SUPER_ADMIN_EMAIL` / `E2E_SUPER_ADMIN_PASSWORD` | — | SUPER_ADMIN credentials (password already changed). |
| `E2E_EXPIRE_RETENTION_COMMAND` | empty | Shell command that ends the 90-day retention of `{tenantId}` **in a disposable local database**. Without it the purge step is skipped. Never point it at a shared or production database. |

## Contract check

To validate the api answers against `contracts/platform.openapi.yaml` while walking, put Prism in proxy mode in
front of the api and look for `Violation` in its output:

```bash
npx prism proxy ../contracts/platform.openapi.yaml http://localhost:8080/api/v1 --port 4075 --errors=false
```
