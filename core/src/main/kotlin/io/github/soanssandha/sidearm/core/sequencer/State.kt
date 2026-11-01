package io.github.soanssandha.sidearm.core.sequencer

/** Where the press sequencer is. Times are uptime ms, the clock of `KeyEvent.getEventTime()`. */
sealed interface State {
    /** The most recent hold's generation (0 before the first). A timer for any other generation does nothing. */
    val generation: Long

    /**
     * No camera launch is held. [lastFlushedPress] is the press that started the latest hold that ended by opening
     * the camera, so a third press of that sequence is still recognised after it lost the race to the timer.
     */
    data class Idle(override val generation: Long = 0, val lastFlushedPress: Long? = null) : State

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
