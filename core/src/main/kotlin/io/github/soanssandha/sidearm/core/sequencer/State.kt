package io.github.soanssandha.sidearm.core.sequencer

/** Where the press sequencer is. Times are uptime ms, the clock of `KeyEvent.getEventTime()`. */
sealed interface State {
    /** The most recent hold's generation (0 before the first). A timer for any other generation does nothing. */
    val generation: Long

    /**
     * Until when the hook should report SystemUI's camera starts ([Event.CameraStartRequested]), or null when no
     * sequence expects one. The activity-start hook sees every activity start on the phone, so this keeps it to one
     * comparison the rest of the time.
     */
    fun startWatchUntil(timing: Timing): Long? = null

    /**
     * No camera launch is held. [lastCameraPress] is the latest press 2 whose camera has already run, either let
     * straight through on an unlocked phone or released from a hold, so a third press of that sequence still opens
     * the target, over the camera.
     */
    data class Idle(override val generation: Long = 0, val lastCameraPress: Long? = null) : State

    /** The camera launch requested by the press at [pressTime] is held until [deadline]. */
    data class HoldingCamera(
        override val generation: Long,
        val pressTime: Long,
        val deadline: Long,
        val camera: CameraArgs,
    ) : State

    /**
     * Clean launch mode, unlocked: the camera request of the press at [pressTime] went to SystemUI at once, and its
     * camera activity start hasn't reached ActivityTaskManager yet. The window ends at [deadline].
     */
    data class AwaitingStart(override val generation: Long, val pressTime: Long, val deadline: Long) : State {
        override fun startWatchUntil(timing: Timing) = pressTime + timing.lateStartMs
    }

    /** Clean launch mode: SystemUI's camera start for the press at [pressTime] is held until [deadline]. */
    data class HoldingStart(override val generation: Long, val pressTime: Long, val deadline: Long) : State {
        override fun startWatchUntil(timing: Timing) = pressTime + timing.lateStartMs
    }

    /**
     * Clean launch mode: press 3 opened the target before SystemUI's camera start arrived. A start that comes by
     * [until] belongs to that press 2 and is dropped; a timer for [generation] ends the window at [until].
     */
    data class DroppingStart(override val generation: Long, val until: Long) : State {
        override fun startWatchUntil(timing: Timing) = until
    }

    companion object {
        val INITIAL: State = Idle()
    }
}
