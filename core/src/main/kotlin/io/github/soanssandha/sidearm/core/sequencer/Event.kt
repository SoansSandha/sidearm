package io.github.soanssandha.sidearm.core.sequencer

/** Something the hook observed. Times are uptime ms. */
sealed interface Event {
    /** Stock asked for the camera from the power double-press at [pressTime]. */
    data class CameraRequested(val pressTime: Long, val camera: CameraArgs) : Event

    /** A press that stock counted, with stock's consecutive tap count after counting it. */
    data class PowerDown(val eventTime: Long, val taps: Int, val interactive: Boolean) : Event

    /** The timer scheduled for [generation] fired. */
    data class TimerFired(val generation: Long) : Event

    /** The config changed. [reduce] receives the new one. */
    data object ConfigChanged : Event

    /** The hook is going away (hot reload or unload), so nothing may stay held. */
    data object Released : Event
}
