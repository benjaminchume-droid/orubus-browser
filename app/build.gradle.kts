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
    compileSdkMinor = 1

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    defaultConfig {
        applicationId = "app.orubus.browser"
        minSdk = 29
        targetSdk = 37
        versionCode = 2
        versionName = "0.2.0"
    }

    // Ship only the native Chromium engine for the target CPU architecture.
    // A universal APK duplicates large CEF binaries for ARM64 and x86_64.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
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
            // Keep shrinking off until Cefrium/CEF JNI and reflection requirements
            // are verified with release-mode device tests.
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
    implementation("info.guardianproject:tor-android:0.4.9.12")
    implementation("info.guardianproject:jtorctl:0.4.5.7")
    implementation("com.wireguard.android:tunnel:1.0.20260102")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("androidx.core:core:1.16.0")
    implementation("androidx.browser:browser:1.8.0")
    testImplementation("junit:junit:4.13.2")
}
