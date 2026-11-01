plugins {
    id("sidearm.android.library")
}

android {
    namespace = "io.github.soanssandha.sidearm.hook"
}

dependencies {
    // Provided at runtime by Vector inside system_server; never bundled.
    compileOnly(libs.libxposed.api)
}
