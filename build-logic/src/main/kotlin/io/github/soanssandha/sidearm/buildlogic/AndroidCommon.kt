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

/** Local unit tests run on JUnit 5 through kotlin.test, the same as in `:core`. */
internal fun Project.configureUnitTests(android: CommonExtension) {
    android.testOptions.unitTests.all { it.useJUnitPlatform() }
    // Lets tests create framework objects such as KeyEvent; their methods return defaults.
    android.testOptions.unitTests.isReturnDefaultValues = true
    dependencies.add("testImplementation", libs.library("kotlin-test-junit5"))
}
