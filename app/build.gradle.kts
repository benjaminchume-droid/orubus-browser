plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystorePath = System.getenv("ORUBUS_KEYSTORE_PATH")
val releaseStorePassword = System.getenv("ORUBUS_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("ORUBUS_KEY_ALIAS")
val releaseKeyPassword = System.getenv("ORUBUS_KEY_PASSWORD")
val releaseSigningReady = listOf(
    releaseKeystorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }

android {
    namespace = "app.orubus.browser"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.orubus.browser"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    if (releaseSigningReady) {
        signingConfigs {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystorePath))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = false
            // Signed only when the release job provides all four ORUBUS_* environment values.
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    kotlinOptions { jvmTarget = "17" }
}
