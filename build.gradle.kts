plugins {
    // Declared here so the build-logic convention plugins find them on the classpath.
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.spotless)
}

// Formatting for every Kotlin source and build script, including build-logic. ktlint reads .editorconfig.
spotless {
    // .gitattributes pins *.kt and *.kts to LF. Spotless's default policy reads git attributes across the
    // project, which breaks the configuration cache on Windows (it trips over the daemon's lock files).
    lineEndings = com.diffplug.spotless.LineEnding.UNIX
    val ktlintVersion = libs.versions.ktlint.get()
    val excluded = arrayOf("**/build/**", "**/.gradle/**", "**/.kotlin/**", "third_party/**")
    kotlin {
        target("**/*.kt")
        targetExclude(*excluded)
        ktlint(ktlintVersion)
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude(*excluded)
        ktlint(ktlintVersion)
    }
}
