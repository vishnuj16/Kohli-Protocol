import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
}

// Optional dev fallback for AI keys: put GEMINI_API_KEY / ANTHROPIC_API_KEY in local.properties
// (never commit it). Keys entered in the app's AI settings take precedence.
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun localKey(name: String): String = "\"" + localProperties.getProperty(name, "") + "\""

android {
    namespace = "com.vishnu.kohliprotocol"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vishnu.kohliprotocol"
        minSdk = 26
        targetSdk = 34
        // Upgrade contract: versionCode only ever increases, every Room schema change bumps the
        // DB version with a migration (never destructive), and every APK is signed with the same
        // key. Then `adb install -r` keeps the database, DataStore, Keystore-encrypted API keys
        // and photos in filesDir.
        versionCode = 2
        versionName = "1.1"

        buildConfigField("String", "GEMINI_API_KEY", localKey("GEMINI_API_KEY"))
        buildConfigField("String", "ANTHROPIC_API_KEY", localKey("ANTHROPIC_API_KEY"))
    }

    buildTypes {
        release {
            // Non-debuggable build: Compose runs much faster than in debug. Minification stays off
            // to avoid any reflection surprises. Signed with the local debug key so it installs
            // over the debug build (same signature, data kept) — fine for a sideloaded personal app.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions {
        // Must match the Kotlin version (1.9.22 -> 1.5.10).
        kotlinCompilerExtensionVersion = libs.versions.composeCompiler.get()
    }
}

ksp {
    // Exported schemas are the baseline for writing Room migrations later.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)

    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.squareup.okhttp)

    implementation(libs.androidx.biometric)
}
