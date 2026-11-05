package io.github.soanssandha.sidearm.core.sequencer

/** The held camera that [Effect.LaunchTarget] runs if the target can't be opened, so press 3 still does something. */
sealed interface Fallback {
    /** The held `handleCameraGesture` call (a locked phone). */
    data class Camera(val camera: CameraArgs) : Fallback

    /** The held camera activity start (the clean launch mode). Dropped once the target opens. */
    data object HeldStart : Fallback

    /**
     * SystemUI's camera start that hasn't come yet (the clean launch mode): if the target can't be opened, the hook
     * reports [Event.TargetUnavailable], and the start runs when it comes instead of being dropped.
     */
    data object PendingStart : Fallback
}
