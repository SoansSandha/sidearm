package io.github.soanssandha.sidearm.hook.effect

import io.github.libxposed.api.XposedInterface
import java.util.concurrent.atomic.AtomicReference

/**
 * The SystemUI camera start held in the clean launch mode, replayed through an [original] invoker that skips every
 * hook, as the caller that made it ([identity]). Skipping our own hook matters: going through it would hold the
 * replay again.
 *
 * The sequencer [hold]s a call inside its lock, so a timer can't try to replay it before it's stored. Each release
 * [take]s it out first, so a call runs or is dropped exactly once.
 */
internal class HeldStart(
    private val original: XposedInterface.Invoker<*, *>,
    private val identity: CallerIdentity,
    private val startSuccess: Int,
    private val log: HookLog,
) {
    /**
     * One `startActivityAsUser` call: the service, its arguments, and its caller's identity token. Compared by
     * identity on purpose: a data class would compare the argument array by reference anyway.
     */
    class Call(val service: Any, val args: Array<Any?>, val callerIdentity: Long)

    private val held = AtomicReference<Call?>(null)

    fun hold(call: Call) {
        // Only one start is held at a time; a second one ends the first hold before it's stored.
        if (held.getAndSet(call) != null) log.warn("A held camera start was replaced without running")
    }

    /** Takes the held call out, if there is one. */
    fun take(): Call? = held.getAndSet(null)

    /** Replays [call] as its caller. */
    fun run(call: Call) {
        val result = identity.runAs(call.callerIdentity) { original.invoke(call.service, *call.args) }
        if (result != startSuccess) log.warn("Held camera start returned $result")
    }
}
