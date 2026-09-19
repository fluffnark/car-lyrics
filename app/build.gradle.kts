plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.doomslug.carlyrics"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.doomslug.carlyrics"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("androidx.car.app:app:1.8.0-rc01")
    implementation("androidx.car.app:app-projected:1.8.0-rc01")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.car.app:app-testing:1.8.0-rc01")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
