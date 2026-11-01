plugins {
    `kotlin-dsl`
}

dependencies {
    // Provided by the root build's plugin classpath; compileOnly keeps a single copy of each plugin.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "sidearm.android.application"
            implementationClass = "io.github.soanssandha.sidearm.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "sidearm.android.library"
            implementationClass = "io.github.soanssandha.sidearm.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("jvmLibrary") {
            id = "sidearm.jvm.library"
            implementationClass = "io.github.soanssandha.sidearm.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
