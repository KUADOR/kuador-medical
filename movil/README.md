# Medical Kuador · movil

Flutter mobile app of the MEDK project. This skeleton shows the system status
reported by the API (`GET /api/v1/system/info`, see `contracts/identity.openapi.yaml`).

## Configuration

| Variable | How | Default | Example |
|---|---|---|---|
| `API_BASE_URL` | `--dart-define` at build/run time | `http://localhost:8080/api/v1` | `http://10.0.2.2:8080/api/v1` (Android emulator against a local API) |

The value includes the `/api/v1` prefix. Plain HTTP is only allowed in Android
debug builds and for local networking on iOS; other environments must use HTTPS.

## Commands

```bash
flutter pub get
flutter analyze
flutter test
flutter run --dart-define=API_BASE_URL=http://10.0.2.2:8080/api/v1
flutter build apk --dart-define=API_BASE_URL=https://<api-host>/api/v1
```

Requires Flutter stable (Dart >= 3.10) and, for Android builds, JDK 17+.

## Structure

```
lib/
  main.dart                     # App entry point and wiring
  config.dart                   # AppConfig (API_BASE_URL)
  system_status/
    system_info.dart            # SystemInfo model (contract schema)
    system_info_client.dart     # HTTP client for GET /system/info
    system_status_screen.dart   # Status screen (Spanish UI)
test/
  system_status_test.dart       # Widget tests with a fake HTTP client
```
