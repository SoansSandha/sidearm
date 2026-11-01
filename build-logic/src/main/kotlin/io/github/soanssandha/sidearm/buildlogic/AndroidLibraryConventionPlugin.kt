package io.github.soanssandha.sidearm.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * `sidearm.android.library`: Android library modules such as `:hook`. Local unit tests run on JUnit 5 through
 * kotlin.test, the same as in `:core`.
 */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            extensions.configure<LibraryExtension> {
                configureAndroid(this)
                testOptions.unitTests.all { it.useJUnitPlatform() }
                // Lets tests create framework objects such as KeyEvent; their methods return defaults.
                testOptions.unitTests.isReturnDefaultValues = true
            }
            dependencies.add("testImplementation", libs.library("kotlin-test-junit5"))
        }
    }
}
