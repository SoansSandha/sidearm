package io.github.soanssandha.sidearm.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project

/** Settings shared by every Android module. AGP's built-in Kotlin follows `compileOptions` for its JVM target. */
internal fun Project.configureAndroid(android: CommonExtension) {
    android.compileSdk = libs.versionInt("compileSdk")
    android.defaultConfig.minSdk = libs.versionInt("minSdk")
    android.compileOptions.sourceCompatibility = libs.javaVersion
    android.compileOptions.targetCompatibility = libs.javaVersion
}
