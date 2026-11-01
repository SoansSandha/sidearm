package io.github.soanssandha.sidearm.core.sequencer

/** The arguments of a held `handleCameraGesture(useWakelock, source)` call, replayed when the camera runs. */
data class CameraArgs(val useWakelock: Boolean, val source: Int)
