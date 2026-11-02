package io.github.soanssandha.sidearm.buildlogic

private val SEMVER = Regex("""(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""")
private const val PART_LIMIT = 100

/**
 * The release `versionCode` for a `MAJOR.MINOR.PATCH` version: `MAJOR * 10000 + MINOR * 100 + PATCH`, so 0.5.0 is 500
 * and 1.2.3 is 10203. Minor and patch must stay below 100 for the codes to grow with the version. The release
 * workflow checks the built APK against the same formula.
 */
fun releaseVersionCode(version: String): Int {
    val parts = SEMVER.matchEntire(version)?.groupValues?.drop(1)?.map(String::toInt)
        ?: error("Version '$version' must be MAJOR.MINOR.PATCH")
    val (major, minor, patch) = parts
    require(minor < PART_LIMIT && patch < PART_LIMIT) { "Version '$version': minor and patch must be below 100" }
    return (major * PART_LIMIT + minor) * PART_LIMIT + patch
}
