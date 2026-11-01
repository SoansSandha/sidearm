package io.github.soanssandha.sidearm.core.config

/**
 * The user's settings as the hook uses them. Build one from stored values with [ConfigCodec.decode].
 *
 * With [dryRun] set, the hook still runs the sequencer, but it only logs what it would do and leaves every press
 * to stock. It's for checking the hook on a new build without changing what the power button does.
 */
data class Config(val enabled: Boolean, val target: TargetComponent?, val dryRun: Boolean = false) {
    /** The app a triple press opens, or null when the feature is off or no app is chosen yet. */
    val armedTarget: TargetComponent?
        get() = target?.takeIf { enabled }

    companion object {
        val DEFAULT = Config(enabled = false, target = null, dryRun = false)
    }
}
