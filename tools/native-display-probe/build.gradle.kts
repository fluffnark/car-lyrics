plugins { id("com.android.application") version "8.13.0" }

android {
    namespace = "com.doomslug.carlyrics.displayprobe"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.doomslug.carlyrics.displayprobe"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs.getByName("debug") { storeFile = file("probe-debug.keystore") }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
