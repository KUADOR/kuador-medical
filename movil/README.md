# Medical Kuador · movil

Flutter mobile app of the MEDK project. It covers authentication and session
against `contracts/user-registration.openapi.yaml` (login with clinic and branch
selection, branch switch, password reset, invitation acceptance, email
verification and self-registration), deep links for the one-time token flows,
the menu built from `GET /me` and the system status screen
(`GET /api/v1/system/info`, `contracts/identity.openapi.yaml`).

## Configuration

| Variable | How | Default | Example |
|---|---|---|---|
| `API_BASE_URL` | `--dart-define` at build/run time | `http://localhost:8080/api/v1` | `http://10.0.2.2:8080/api/v1` (Android emulator against a local API) |
| `APP_LINK_HOST` | `--dart-define` (Dart and Android manifest); iOS: `ios/Flutter/AppLinks.local.xcconfig` | empty in Dart (any `https` host is accepted, local only); `localhost` in the manifest and entitlements | `links.example.com` |
| `CONSENT_TEXT_VERSION` | `--dart-define` | `2026-01` | `2026-01` |

- `API_BASE_URL` includes the `/api/v1` prefix. Plain HTTP is only allowed in
  Android debug builds and for local networking on iOS; other environments must
  use HTTPS.
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
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1
flutter build apk --dart-define=API_BASE_URL=https://<api-host>/api/v1 --dart-define=APP_LINK_HOST=<deep-link-host>
flutter build ios --dart-define=API_BASE_URL=https://<api-host>/api/v1 --dart-define=APP_LINK_HOST=<deep-link-host>
```

Requires Flutter stable (Dart >= 3.10) and, for Android builds, JDK 17+.

## Structure

```
lib/
  main.dart                 # Entry point and wiring of the platform services
  app.dart, app_scope.dart  # Root widget, session-driven navigation, services
  config.dart               # AppConfig (API_BASE_URL, APP_LINK_HOST, CONSENT_TEXT_VERSION)
  core/
    api/                    # HTTP client with bearer token, refresh and API errors
    session/                # Session controller, token store, /me models
    menu/                   # Menu built from /me
    connectivity/           # Connection state and offline banner
  features/
    auth/data/              # Auth endpoints and models
    auth/deep_links/        # Deep link parsing and platform source
    auth/ui/                # Login, clinic/branch selection, recovery, invitation, verification, self-registration
    home/                   # Home with the menu and branch switch
  system_status/            # System status screen
test/
  core/                     # Session controller and units
  features/                 # Auth flows and self-registration (widget tests)
  support/fake_api.dart     # Fake API used by the tests
  system_status_test.dart
```
