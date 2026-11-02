import io.github.soanssandha.sidearm.buildlogic.releaseVersionCode

plugins {
    id("sidearm.android.application")
}

android {
    namespace = "io.github.soanssandha.sidearm"

    defaultConfig {
        applicationId = "io.github.soanssandha.sidearm"
        // Releases derive versionCode from the version (0.5.0 is 500). Vector hot-reloads a running module only
        // when versionCode changes, so scripts/dev-install.sh passes a fresh one for each dev install.
        versionCode = providers.gradleProperty("sidearm.versionCode")
            .map { requireNotNull(it.toIntOrNull()) { "sidearm.versionCode must be an integer, was '$it'" } }
            .getOrElse(releaseVersionCode(libs.versions.sidearm.get()))
        versionName = libs.versions.sidearm.get()
    }
}

dependencies {
    implementation(project(":core"))
    // Packaged into the APK for Vector to load, but not importable: the UI and the hook run in different processes.
    runtimeOnly(project(":hook"))
    compileOnly(libs.libxposed.api)
    // The app's side of Vector: its service binder and the remote preferences the hook reads.
    implementation(libs.libxposed.service)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    testImplementation(libs.kotlinx.coroutines.test)
}
