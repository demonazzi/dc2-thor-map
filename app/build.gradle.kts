plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ru.misha.dc2map"
    compileSdk = 34

    defaultConfig {
        applicationId = "ru.misha.dc2map"
        minSdk = 26
        targetSdk = 34
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }

    // One fixed key in the repo, so every new build installs over the old one.
    signingConfigs {
        create("fixed") {
            storeFile = rootProject.file("dc2map.keystore")
            storePassword = "dc2map"
            keyAlias = "dc2map"
            keyPassword = "dc2map"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("fixed") }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.webkit:webkit:1.11.0")
}
