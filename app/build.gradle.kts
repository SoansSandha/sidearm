plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "io.github.soanssandha.sidearm"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.soanssandha.sidearm"
        minSdk = 37
        targetSdk = 37
        // Vector hot-reloads a running module only when versionCode changes; scripts/dev-install.sh
        // passes a fresh one so hook changes reach system_server without a reboot.
        versionCode = providers.gradleProperty("sidearm.versionCode")
            .map { requireNotNull(it.toIntOrNull()) { "sidearm.versionCode must be an integer, was '$it'" } }
            .getOrElse(1)
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":hook"))
    compileOnly(libs.libxposed.api)
}
