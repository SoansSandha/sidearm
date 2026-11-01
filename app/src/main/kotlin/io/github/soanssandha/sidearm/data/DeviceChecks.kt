package io.github.soanssandha.sidearm.data

/** What the config screen checks on the phone itself, outside the framework. */
interface DeviceChecks {
    /** Reads the double-press setting. This queries the settings provider, so keep it off the main thread. */
    fun doublePress(): DoublePress

    val build: BuildCheck
}
