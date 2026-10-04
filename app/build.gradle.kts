import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.doomslug.carlyrics"
    compileSdk = 36
    buildFeatures { buildConfig = true; aidl = true }
    defaultConfig {
        applicationId = "com.doomslug.carlyrics"
        minSdk = 29
        targetSdk = 36
        versionCode = 17
        versionName = "0.5.1"
        manifestPlaceholders["carAppCategory"] = "androidx.car.app.category.POI"
        buildConfigField("boolean", "FULLSCREEN_HOST", "false")
    }
    signingConfigs {
        val signingFile = rootProject.file("signing/keystore.properties")
        if (signingFile.exists()) {
            val properties = Properties().apply { signingFile.inputStream().use(::load) }
            create("release") {
                storeFile = rootProject.file(properties.getProperty("storeFile"))
                storePassword = properties.getProperty("storePassword")
                keyAlias = properties.getProperty("keyAlias")
                keyPassword = properties.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        // Private DHU experiment: navigation hosting can receive a wider surface.
        // This category does not describe karaoke and is not for a Play upload.
        create("fullscreenProbe") {
            initWith(getByName("debug"))
            // Same package as release; only replaces installs with the same local signing key.
            if (rootProject.file("signing/keystore.properties").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            versionNameSuffix = "-fullscreen-probe"
            matchingFallbacks += "debug"
            manifestPlaceholders["carAppCategory"] = "androidx.car.app.category.NAVIGATION"
            buildConfigField("boolean", "FULLSCREEN_HOST", "true")
        }
        create("nativeProbe") {
            initWith(getByName("fullscreenProbe"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-native-dev"
            matchingFallbacks += listOf("fullscreenProbe", "debug")
        }
        // Match the Play POI layout on the DHU without replacing the Play install.
        create("poiProbe") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-poi-dev"
            matchingFallbacks += "debug"
            if (rootProject.file("signing/keystore.properties").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = false
            if (rootProject.file("signing/keystore.properties").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

dependencies {
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("androidx.car.app:app:1.8.0-rc01")
    implementation("androidx.car.app:app-projected:1.8.0-rc01")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.car.app:app-testing:1.8.0-rc01")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
