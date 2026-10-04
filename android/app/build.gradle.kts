plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// The one place the version lives: gradle.properties `ryciny.version`, semver MAJOR.MINOR.PATCH.
// versionCode follows from it (0.1.0 -> 100, 1.2.3 -> 10203) so it always grows with the version.
val appVersion = providers.gradleProperty("ryciny.version").get()
val appVersionCode = appVersion.split(".").map(String::toInt).let { (major, minor, patch) -> major * 10_000 + minor * 100 + patch }

android {
    namespace = "pl.wojczal.ryciny"
    compileSdk = 37

    defaultConfig {
        applicationId = "pl.wojczal.ryciny"
        minSdk = 29
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
        // Phones are arm64; x86_64 only for the emulator on an Intel machine.
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Test builds only: signed with the debug key so the APK installs straight away.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures { compose = true }

    // TFLite models are memory-mapped straight out of the APK, which needs them stored uncompressed.
    androidResources { noCompress += "tflite" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.google.ai.edge.litert:litert:1.4.1")
    testImplementation("junit:junit:4.13.2")
}

// style.json, dog_breeds.json and aircraft_types.json live in ../shared so the Raspberry Pi version reads the same files.
android.sourceSets.getByName("main").assets.srcDir("../../shared")
