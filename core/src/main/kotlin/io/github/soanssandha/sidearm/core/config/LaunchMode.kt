package io.github.soanssandha.sidearm.core.config

/**
 * How a triple press opens the app on an unlocked phone (docs/plan.md §3.7). On a locked phone both modes behave
 * the same: the camera is held in GestureLauncherService and the unlock prompt comes first.
 *
 * [stored] is the value kept in [ConfigKeys.LAUNCH_MODE]; renaming one resets that choice on existing installs.
 */
enum class LaunchMode(val stored: String) {
    /** The camera opens at press 2 as in stock, and press 3 opens the app over it. */
    CLASSIC("classic"),

    /**
     * Press 2's camera activity is held until press 3 or the press window ends, so the app opens with no camera
     * first. A double press opens the camera one window later than stock.
     */
    CLEAN("clean"),
    ;

    companion object {
        /** Reads a stored value. Anything missing or unknown is [CLASSIC], which works on every tested build. */
        fun fromStored(stored: String?): LaunchMode = entries.firstOrNull { it.stored == stored } ?: CLASSIC
    }
}
