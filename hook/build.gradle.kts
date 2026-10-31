plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.soanssandha.sidearm.hook"
    compileSdk = 37

    defaultConfig {
        minSdk = 37
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Provided at runtime by Vector inside system_server; never bundled.
    compileOnly(libs.libxposed.api)
}
