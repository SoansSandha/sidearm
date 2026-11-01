package io.github.soanssandha.sidearm.core.sequencer

import io.github.soanssandha.sidearm.core.config.TargetComponent

/** Something the hook must do, in list order, after releasing the sequencer lock. */
sealed interface Effect {
    /** `handleCameraGesture`: skip the original for now and return true, as stock does after a launch. */
    data object HoldCamera : Effect

    /** `handleCameraGesture`: run the original now. */
    data object RunCameraNow : Effect

    /** Deliver [Event.TimerFired] for [generation] at uptime [deadline]. */
    data class ScheduleTimer(val deadline: Long, val generation: Long) : Effect

    /** Drop the pending timer for [generation]. */
    data class CancelTimer(val generation: Long) : Effect

    /** Run the held `handleCameraGesture` call now. */
    data class RunHeldCamera(val camera: CameraArgs) : Effect

    /** `interceptPowerKeyDown`: return true, so PhoneWindowManager consumes the press. */
    data object ConsumePress : Effect

    /** Wake the device with `WAKE_REASON_POWER_BUTTON`; the press came while it wasn't interactive. */
    data object WakeDevice : Effect

    /** Open [target] (through the unlock prompt if the keyguard is showing), or the camera if it no longer resolves. */
    data class LaunchTarget(val target: TargetComponent, val fallback: CameraArgs) : Effect
}
