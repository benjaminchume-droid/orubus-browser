plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

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

    buildTypes {
        debug { applicationIdSuffix = ".debug" }
        release {
            isMinifyEnabled = false
            // Release signing is intentionally not configured with secrets in source.
        }
    }

    kotlinOptions { jvmTarget = "17" }
}
