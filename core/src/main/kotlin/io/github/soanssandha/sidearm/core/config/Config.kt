package io.github.soanssandha.sidearm.core.config

/** The user's settings as the hook uses them. Build one from stored values with [ConfigCodec.decode]. */
data class Config(val enabled: Boolean, val target: TargetComponent?) {
    /** The app a triple press opens, or null when the feature is off or no app is chosen yet. */
    val armedTarget: TargetComponent?
        get() = target?.takeIf { enabled }

    companion object {
        val DEFAULT = Config(enabled = false, target = null)
    }
}
