plugins {
    id("com.android.application")
    id("com.cefrium")
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
    compileSdk = 37

    defaultConfig {
        applicationId = "app.orubus.browser"
        minSdk = 29
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
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
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

configurations.configureEach {
    exclude(group = "com.google.guava", module = "listenablefuture")
}

dependencies {
    implementation("com.cefrium:cefrium-sdk:0.9.4")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.browser:browser:1.8.0")
}
