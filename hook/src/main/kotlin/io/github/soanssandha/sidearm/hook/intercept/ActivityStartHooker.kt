package io.github.soanssandha.sidearm.hook.intercept

import io.github.libxposed.api.XposedInterface
import io.github.soanssandha.sidearm.hook.effect.CallerIdentity
import io.github.soanssandha.sidearm.hook.effect.HeldStart
import io.github.soanssandha.sidearm.hook.effect.HookLog
import io.github.soanssandha.sidearm.hook.effect.Sequencer

/**
 * Hooks `ActivityTaskManagerService.startActivityAsUser`, the binder entry every activity start on the phone goes
 * through. In the clean launch mode, SystemUI's camera start after an unlocked power double-press goes to the
 * sequencer, which holds or drops it (ADR 0010). Every other start, and every start while no clean sequence expects
 * one, goes straight to the original after a single comparison.
 *
 * A held or dropped start returns [startSuccess], as stock does when the camera starts; SystemUI ignores the result.
 */
internal class ActivityStartHooker(
    private val sequencer: Sequencer,
    private val matcher: CameraStartMatcher,
    private val identity: CallerIdentity,
    private val startSuccess: Int,
    private val uptimeMillis: () -> Long,
    private val log: HookLog,
) : XposedInterface.Hooker {

    override fun intercept(chain: XposedInterface.Chain): Any? {
        val watchUntil = sequencer.startWatchUntil
        if (watchUntil == Sequencer.NOT_WATCHING) return chain.proceed()
        val answered = runCatching { onStart(chain, watchUntil) }.getOrElse { error ->
            log.warn("Activity start not handled; running it now", error)
            false
        }
        return if (answered) startSuccess else chain.proceed()
    }

    /** Hands SystemUI's camera start to the sequencer. Returns true if the sequencer held or dropped it. */
    private fun onStart(chain: XposedInterface.Chain, watchUntil: Long): Boolean {
        val now = uptimeMillis()
        if (now > watchUntil || !matcher.matches(chain)) return false
        val call = HeldStart.Call(checkNotNull(chain.thisObject), chain.args.toTypedArray(), identity.capture())
        return sequencer.dispatchStart(now, call)
    }
}
