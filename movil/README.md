# Medical Kuador · movil

Flutter mobile app of the MEDK project. It covers authentication and session
against `contracts/user-registration.openapi.yaml` (login with clinic and branch
selection, branch switch, password reset, invitation acceptance, email
verification and self-registration), deep links for the one-time token flows,
the menu built from `GET /me`, the staff, roles and patients modules (MEDK-24)
and the system status screen (`GET /api/v1/system/info`,
`contracts/identity.openapi.yaml`).

## Modules

| Menu entry | Shown when (`GET /me`) | Screens |
|---|---|---|
| Personal | module `identity` + `identity.users.read` | List with search and status filter; invitation (`identity.users.manage`) with branch roles; detail with edit, resend invitation, disable/enable; branch roles (`identity.assignments.manage`) |
| Roles y permisos | module `identity` + `roles.read` | List; create (`roles.create`), edit (`roles.update`) and delete (`roles.delete`) over the clinic-scoped catalog of `GET /permissions` |
| Pacientes | module `patients` + `patients.read` | List with search; registration with LOPDP consent and legal guardian for minors (`patients.register`); detail with edit (`patients.edit`), consent, account invitation and guardian links (`patients.guardians.manage`) |
| Autorregistro de pacientes | module `patients` + `patients.selfRegistration.configure` | Enable or disable self-registration |
| Mis contactos autorizados | `PATIENT` account + module `patients` | Links (including those ended at adulthood) and authorization of an emergency contact or delegated access |

Rules shared with the web (same contract, same Spanish messages):

- The UI only offers what can succeed (no OWNER for non-owners, permissions the
  person lacks cannot be added to a role, nobody acts on their own record); the
  api enforces every rule and its errors are shown in Spanish
  (`core/api/error_messages.dart`).
- Every creation sends a client id as body `id` and `Idempotency-Key`. The id
  is kept while the same data is retried and renewed when the data changes
  (`core/util/idempotent_creation.dart`).
- Every mutating action checks the connection first; offline, nothing is sent
  and nothing is stored on the phone (FR-029).
- Document types, sex values and the consent text are [POR DEFINIR]; the
  provisional codes live in `features/auth/data/registration_catalog.dart`.

## Configuration

| Variable | How | Default | Example |
|---|---|---|---|
| `API_BASE_URL` | `--dart-define` at build/run time (`lib/core/config/api_config.dart`) | `http://10.0.2.2:8080` (local api from the Android emulator) | iOS simulator `http://localhost:8080`; physical device `http://<LAN-IP>:8080`; other environments `https://<api-host>` |
| `APP_LINK_HOST` | `--dart-define` (Dart and Android manifest); iOS: `ios/Flutter/AppLinks.local.xcconfig` | empty in Dart (any `https` host is accepted, local only); `localhost` in the manifest and entitlements | `links.example.com` |
| `CONSENT_TEXT_VERSION` | `--dart-define` | `2026-01` | `2026-01` |

- `API_BASE_URL` is the api origin; the app appends `/api/v1`. A value that
  already ends in `/api/v1` is accepted too. A value that is not an `http` or
  `https` URL stops the app at start-up with an error that names the variable.
- Plain HTTP is only allowed in debug builds: on Android through
  `android/app/src/debug/res/xml/network_security_config.xml` (only merged into
  debug builds); on iOS through `NSAllowsLocalNetworking` in `Info.plist`, which
  the "Strip Local Networking" build phase
  (`ios/scripts/strip_local_networking.sh`) removes from Release and Profile
  builds. Other environments must use HTTPS.
- When the api cannot be reached (refused connection, no network or a 15 s
  timeout) the screens say so in Spanish; requests, tokens and personal data
  are never printed or logged (`test/features/local_api_connection_test.dart`
  checks it).

### Local api

Start the api as described in `docs/deployment.md` («Entorno local»), then:

```bash
flutter run                                                        # Android emulator (default http://10.0.2.2:8080)
flutter run --dart-define=API_BASE_URL=http://localhost:8080       # iOS simulator
flutter run --dart-define=API_BASE_URL=http://192.168.1.50:8080    # physical device: use your machine's LAN IP
```

A physical device must be on the same network as the machine, the api must
listen on all interfaces and the firewall must allow port 8080.
- `APP_LINK_HOST` is the host of `KUADOR_APP_LINK_BASE_URL` of the api, without
  scheme. On Android, Gradle reads it from the `--dart-define` values and sets
  the `autoVerify` intent filter of `AndroidManifest.xml`. On iOS, Associated
  Domains (`Runner/Runner.entitlements`) use the `APP_LINK_HOST` build setting
  of `ios/Flutter/AppLinks.xcconfig`; override it per environment in
  `ios/Flutter/AppLinks.local.xcconfig` (not committed). Pass the same host to
  `--dart-define` so the app only accepts links of that domain.

## Deep links

Links have the form `https://<APP_LINK_HOST>/<route>#t=<token>` (ADR-014), with
the routes fixed in the api: `/invitation`, `/password-reset` and
`/email-verification`. The token is read from the fragment, sent only in request
bodies and never logged. Links are handled by `app_links`
(`flutter_deeplinking_enabled` is off). The domain must serve
`/.well-known/assetlinks.json` and `/.well-known/apple-app-site-association`
(see `docs/deployment.md`, "Deep links").

Try them locally without domain verification:

```bash
adb shell am start -a android.intent.action.VIEW -d "https://localhost/invitation#t=<token>"
xcrun simctl openurl booted "https://localhost/invitation#t=<token>"
```

## Session

- Access and refresh tokens are stored in the Keychain (iOS, this device only)
  or Keystore-backed storage (Android) with `flutter_secure_storage`; never in
  plain preferences or logs.
- A `401` refreshes the session once and retries; a failed refresh signs out.
- The menu shows only what the clinic plan and the active assignment allow
  (`GET /me`); the api still checks every operation.
- Without a connection, forms do not send anything and nothing is stored to be
  sent later.

## Commands

```bash
flutter pub get
flutter analyze
flutter test
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080
flutter build apk --dart-define=API_BASE_URL=https://<api-host> --dart-define=APP_LINK_HOST=<deep-link-host>
flutter build ios --dart-define=API_BASE_URL=https://<api-host> --dart-define=APP_LINK_HOST=<deep-link-host>
```

Requires Flutter stable (Dart >= 3.10) and, for Android builds, JDK 17+.

### Goldens

Golden tests carry the tag `golden` (`dart_test.yaml`) and live in `test/goldens/`. CI runs them
on Linux with Flutter `3.47.9` (job `movil`), so the baselines are the Linux renderings; macOS
renders text and antialiasing slightly differently, so `flutter test` skips the goldens outside Linux. When the job fails, it renders the goldens
again on the runner and uploads them, with the diffs, as the artifact `movil-goldens-linux`:
after reviewing them, copy the new PNG files into `test/goldens/` and commit them in the same PR.
With Docker, the same can be done locally (there is no published Flutter image for `3.47.9`, so
the script clones the SDK at that tag into a Docker volume the first time):

```bash
scripts/update-goldens.sh
```

## Structure

```
lib/
  main.dart                 # Entry point and wiring of the platform services
  app.dart, app_scope.dart  # Root widget, session-driven navigation, services
  config.dart               # AppConfig (API_BASE_URL, APP_LINK_HOST, CONSENT_TEXT_VERSION)
  core/
    config/                 # ApiConfig: reads and validates API_BASE_URL
    api/                    # HTTP client with bearer token, refresh, API errors, Spanish messages, pages
    ui/                     # Module page, paged search list, shared form helpers
    session/                # Session controller, token store, /me models
    menu/                   # Menu built from /me
    connectivity/           # Connection state and offline banner
    util/                   # Client ids and idempotent creations
  features/
    auth/data/              # Auth endpoints and models
    auth/deep_links/        # Deep link parsing and platform source
    auth/ui/                # Login, clinic/branch selection, recovery, invitation, verification, self-registration
    home/                   # Home with the menu and branch switch
    staff_users/            # Staff list, invitation, detail and branch role assignments
    roles/                  # Clinic roles over the permission catalog
    patients/               # Patients, consent, guardians, adult links, self-registration settings
  system_status/            # System status screen
test/
  core/                     # Session controller and units
  features/                 # Auth, staff, roles, patients and patient account (widget tests)
  support/fake_api.dart     # Fake API used by the tests
  support/module_fixtures.dart # Contract examples of the module tests
  system_status_test.dart
```
