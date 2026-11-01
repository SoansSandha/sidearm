package io.github.soanssandha.sidearm.hook.intercept

import android.view.KeyEvent
import io.github.libxposed.api.XposedInterface
import io.github.soanssandha.sidearm.core.sequencer.Event
import io.github.soanssandha.sidearm.hook.effect.HookLog
import io.github.soanssandha.sidearm.hook.effect.Sequencer
import io.github.soanssandha.sidearm.hook.target.GestureLauncherTargets

/**
 * Hooks `GestureLauncherService.interceptPowerKeyDown(KeyEvent, boolean interactive, MutableBoolean outLaunched)`.
 * Every press stock counted goes to the sequencer, which may consume it so PhoneWindowManager doesn't toggle the
 * screen (docs/plan.md §3.1). The first press of each sequence also records whether the phone was locked
 * ([SequenceStart]). `outLaunched` is never touched.
 */
internal class PowerKeyDownHooker(
    private val targets: GestureLauncherTargets,
    private val sequenceStart: SequenceStart,
    private val sequencer: Sequencer,
    private val log: HookLog,
) : XposedInterface.Hooker {

    override fun intercept(chain: XposedInterface.Chain): Any? {
        // Stock runs exactly once, outside any try. Running it again would count a phantom tap, which means the
        // camera on press 1 and SOS a press early.
        val stockResult = chain.proceed()
        val consumed = runCatching { onPress(chain) }.getOrElse { error ->
            log.warn("Power press not handled; stock's result stands", error)
            false
        }
        return if (consumed) true else stockResult
    }

    /** Hands a press stock counted to the sequencer. Returns true if the sequencer consumed it. */
    private fun onPress(chain: XposedInterface.Chain): Boolean {
        val service = checkNotNull(chain.thisObject)
        val eventTime = (chain.getArg(0) as KeyEvent).eventTime
        if (!targets.wasCounted(service, eventTime)) return false
        val taps = targets.consecutiveTaps(service)
        if (taps == 1) sequenceStart.onFirstPress()
        val interactive = chain.getArg(1) as Boolean
        return sequencer.dispatch(Event.PowerDown(eventTime, taps, interactive))
    }
}
