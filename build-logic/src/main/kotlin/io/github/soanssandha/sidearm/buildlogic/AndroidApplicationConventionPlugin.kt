package io.github.soanssandha.sidearm.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `sidearm.android.application`: the APK module, with Compose for its UI and local unit tests. Release builds are
 * shrunk with R8.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.configure<ApplicationExtension> {
                configureAndroid(this)
                configureUnitTests(this)
                defaultConfig.targetSdk = libs.versionInt("targetSdk")
                buildFeatures.compose = true
                buildTypes.getByName("release").apply {
                    isMinifyEnabled = true
                    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                }
            }
            val composeBom = dependencies.platform(libs.library("androidx-compose-bom"))
            dependencies.add("implementation", composeBom)
            dependencies.add("implementation", libs.library("androidx-compose-ui-tooling-preview"))
            // Layout inspector and preview support, left out of release builds.
            dependencies.add("debugImplementation", libs.library("androidx-compose-ui-tooling"))
        }
    }
}
