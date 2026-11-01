package io.github.soanssandha.sidearm.hook.effect

import io.github.libxposed.api.XposedInterface
import io.github.soanssandha.sidearm.core.sequencer.CameraArgs

/**
 * Replays held `handleCameraGesture` calls through an [original] invoker, which skips every hook. Skipping our own
 * hook matters: going through it would hold the replay again.
 */
internal class HeldCamera(private val original: XposedInterface.Invoker<*, *>) {

    @Volatile
    private var service: Any? = null

    /** Records the GestureLauncherService instance. The camera hooker calls this before every request it handles. */
    fun attach(service: Any) {
        this.service = service
    }

    fun run(camera: CameraArgs) {
        val service = checkNotNull(service) { "No GestureLauncherService has asked for the camera yet" }
        original.invoke(service, camera.useWakelock, camera.source)
    }
}
