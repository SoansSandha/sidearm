package io.github.soanssandha.sidearm.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.version(alias: String): String = findVersion(alias)
    .orElseThrow { IllegalStateException("No version '$alias' in gradle/libs.versions.toml") }
    .requiredVersion

internal fun VersionCatalog.versionInt(alias: String): Int = version(alias).let {
    requireNotNull(it.toIntOrNull()) { "Version '$alias' must be an integer, was '$it'" }
}

internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias)
    .orElseThrow { IllegalStateException("No library '$alias' in gradle/libs.versions.toml") }

internal val VersionCatalog.javaVersion: JavaVersion
    get() = JavaVersion.toVersion(version("jvmTarget"))
