import java.util.Properties

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
        versionCode = 10
        versionName = "0.3.7"
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
    implementation("androidx.car.app:app:1.8.0-rc01")
    implementation("androidx.car.app:app-projected:1.8.0-rc01")
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.car.app:app-testing:1.8.0-rc01")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
