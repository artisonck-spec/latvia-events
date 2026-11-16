plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "lv.latviaevents"
    compileSdk = 36

    defaultConfig {
        applicationId = "lv.latviaevents"
        minSdk = 26
        targetSdk = 36
        // Each GitHub build gets a higher number, so Android accepts it as an update
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // One fixed release key, so every new APK installs over the previous one and keeps your saved events
    signingConfigs {
        create("release") {
            storeFile = file("pasakumi-release.jks")
            storePassword = "pasakumi-lv-2026"
            keyAlias = "pasakumi"
            keyPassword = "pasakumi-lv-2026"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isDebuggable = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.11.0")
}
