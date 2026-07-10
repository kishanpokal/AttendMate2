import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.kishan.attendmate"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.kishan.attendmate"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Read local.properties securely
        val properties = Properties()
        val localPropertiesFile = project.rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            properties.load(localPropertiesFile.inputStream())
        }
        val geminiApiKey = properties.getProperty("GEMINI_API_KEY", "")
        buildConfigField("String", "GEMINI_API_KEY", "\"$geminiApiKey\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true   // ✅ REQUIRED
    }


    // ✅ REQUIRED for Compose (matches BOM 2024.10.01)
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {

    /* ---------------- CORE ---------------- */
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.2")

    /* ---------------- COMPOSE (SINGLE BOM – CORRECT) ---------------- */
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // ✅ REQUIRED for AnimatedVisibility, scaleIn, expandVertically, etc.
    implementation("androidx.compose.animation:animation")
    implementation("com.google.android.material:material:1.11.0")

    // Icons
    implementation("androidx.compose.material:material-icons-extended")

    // Google Fonts
    implementation("androidx.compose.ui:ui-text-google-fonts")

    // Frosted-glass backdrop blur for the floating navigation bar (Compose 1.7 compatible)
    implementation(libs.haze)

    /* ---------------- SPLASH SCREEN ---------------- */
    implementation("androidx.core:core-splashscreen:1.0.1")

    /* ---------------- FIREBASE (SINGLE BOM) ---------------- */
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-analytics")
    implementation(libs.androidx.work.runtime.ktx)

    /* ---------------- TESTING ---------------- */
    testImplementation("junit:junit:4.13.2")
    implementation("androidx.core:core-ktx:1.13.1")


    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // 🔴 THIS IS THE MISSING ONE
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")

    // Optional but recommended
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    implementation ("androidx.glance:glance-appwidget:1.0.0")
    implementation ("androidx.glance:glance-material3:1.0.0")

    /* ---------------- ADMOB ---------------- */
    // AdMob removed

    /* ---------------- AI (Rule-based, no API needed) ---------------- */
    // No external AI SDK required — the AI assistant is fully offline and rule-based

}
