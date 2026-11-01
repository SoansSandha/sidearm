package io.github.soanssandha.sidearm.hook.intercept

import io.github.libxposed.api.XposedInterface
import io.github.soanssandha.sidearm.core.sequencer.CameraArgs
import io.github.soanssandha.sidearm.core.sequencer.Event
import io.github.soanssandha.sidearm.hook.effect.HeldCamera
import io.github.soanssandha.sidearm.hook.effect.HookLog
import io.github.soanssandha.sidearm.hook.effect.Sequencer
import io.github.soanssandha.sidearm.hook.target.GestureLauncherTargets

/**
 * Hooks `GestureLauncherService.handleCameraGesture(boolean useWakelock, int source)`. The power double-press's
 * camera request goes to the sequencer, which may hold it for one press window if the phone was locked when the
 * sequence began ([SequenceStart]). Requests from other sources, such as the lift gesture, pass straight through.
 */
internal class CameraGestureHooker(
    private val targets: GestureLauncherTargets,
    private val sequenceStart: SequenceStart,
    private val sequencer: Sequencer,
    private val heldCamera: HeldCamera,
    private val log: HookLog,
) : XposedInterface.Hooker {

    override fun intercept(chain: XposedInterface.Chain): Any? {
        val held = runCatching { onCameraRequest(chain) }.getOrElse { error ->
            log.warn("Camera request not handled; running it now", error)
            false
        }
        // A held request returns true, as stock does after a launch, so PhoneWindowManager wakes the screen exactly
        // as it does today.
        return if (held) true else chain.proceed()
    }

    /** Hands a power double-press's camera request to the sequencer. Returns true if the sequencer held it. */
    private fun onCameraRequest(chain: XposedInterface.Chain): Boolean {
        val source = chain.getArg(1) as Int
        if (source != targets.powerDoubleTapSource) return false
        val service = checkNotNull(chain.thisObject)
        heldCamera.attach(service)
        // Stock records press 2's event time before it asks for the camera.
        val pressTime = targets.lastPowerDown(service)
        val camera = CameraArgs(useWakelock = chain.getArg(0) as Boolean, source = source)
        return sequencer.dispatch(Event.CameraRequested(pressTime, camera, sequenceStart.startedLocked))
    }
}
