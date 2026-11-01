package io.github.soanssandha.sidearm.hook.effect

import io.github.libxposed.api.XposedInterface
import io.github.soanssandha.sidearm.core.sequencer.CameraArgs

/**
 * Replays held `handleCameraGesture` calls through an [original] invoker, which skips every hook. Skipping our own
 * hook matters: going through it would hold the replay again.
 */
internal class HeldCamera(private val original: XposedInterface.Invoker<*, *>) {

    /** The GestureLauncherService instance, once the camera hooker has seen a request. */
    @Volatile
    var service: Any? = null
        private set

    /** Records the GestureLauncherService instance. The camera hooker calls this before every request it handles. */
    fun attach(service: Any) {
        this.service = service
    }

    fun run(camera: CameraArgs) {
        val service = checkNotNull(service) { "No GestureLauncherService has asked for the camera yet" }
        original.invoke(service, camera.useWakelock, camera.source)
    }
}
