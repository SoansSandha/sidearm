package io.github.soanssandha.sidearm.data

/**
 * The Android builds whose hook targets have been checked, by their full `Build.FINGERPRINT` (`adb shell getprop
 * ro.build.fingerprint`, which ends in `:user/release-keys`; the headings in `docs/hook-targets.md` leave that off).
 * Keep this in step with those sections; the post-OTA checklist in `docs/testing.md` adds each new one.
 *
 * On any other build the hook still checks its targets at boot and stays off if one is missing, so this only drives a
 * warning on the config screen.
 */
object TestedBuilds {
    val FINGERPRINTS: Set<String> = setOf(
        "google/komodo/komodo:17/CP3A.261005.005/16271449:user/release-keys",
    )
}
