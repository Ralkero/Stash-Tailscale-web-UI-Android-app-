plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.localstash.wrapper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.localstash.wrapper"
        minSdk = 26
        targetSdk = 34
        versionCode = 9
        versionName = "1.7.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        create("release") {
            val releaseStore = System.getenv("STASH_WRAPPER_KEYSTORE_FILE")
            if (!releaseStore.isNullOrBlank()) {
                storeFile = file(releaseStore)
                storePassword = System.getenv("STASH_WRAPPER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("STASH_WRAPPER_KEY_ALIAS") ?: "stash-wrapper"
                keyPassword = System.getenv("STASH_WRAPPER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            val releaseStore = System.getenv("STASH_WRAPPER_KEYSTORE_FILE")
            if (!releaseStore.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

kotlin {
    jvmToolchain(17)
}
