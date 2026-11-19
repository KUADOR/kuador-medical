import java.util.Base64

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "com.kuador.medical_kuador"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.kuador.medical_kuador"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        // Host of the App Links (same as KUADOR_APP_LINK_BASE_URL of the api).
        manifestPlaceholders["appLinkHost"] = appLinkHost()
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

flutter {
    source = "../.."
}

/**
 * Reads APP_LINK_HOST from the `--dart-define` values that Flutter passes to Gradle
 * (base64, comma separated), so the Dart code and the manifest use the same host.
 * Falls back to `localhost` for local builds without a deep link domain.
 */
fun appLinkHost(): String {
    val defines = (project.findProperty("dart-defines") as String?).orEmpty()
    val host = defines.split(",")
        .filter { it.isNotBlank() }
        .map { String(Base64.getDecoder().decode(it)) }
        .firstOrNull { it.startsWith("APP_LINK_HOST=") }
        ?.substringAfter("=")
        ?.trim()
        ?.lowercase()
    return if (host.isNullOrEmpty()) "localhost" else host
}
