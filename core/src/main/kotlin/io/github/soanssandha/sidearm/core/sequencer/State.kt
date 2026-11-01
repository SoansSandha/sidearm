package io.github.soanssandha.sidearm.core.sequencer

/** Where the press sequencer is. Times are uptime ms, the clock of `KeyEvent.getEventTime()`. */
sealed interface State {
    /** The most recent hold's generation (0 before the first). A timer for any other generation does nothing. */
    val generation: Long

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

    companion object {
        val INITIAL: State = Idle()
    }
}
