package io.github.soanssandha.sidearm.hook.effect

import android.os.Binder
import android.os.Parcel
import io.github.soanssandha.sidearm.hook.target.KeyguardDismissCallbackTargets
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A one-shot `IKeyguardDismissCallback`, written as a plain `Binder` that answers the interface's transaction codes
 * ([KeyguardDismissCallbackTargets]), so no hidden-API stubs are needed. SystemUI calls it on a binder thread when the
 * unlock succeeds, is cancelled or fails; [onOutcome] gets the first of those and should only hand it on.
 */
internal class DismissCallbackBinder(
    private val callback: KeyguardDismissCallbackTargets,
    private val onOutcome: (KeyguardDismissCallbackTargets.Outcome) -> Unit,
) : Binder(callback.descriptor) {

    private val delivered = AtomicBoolean(false)

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (callback.outcomeOf(code) == null) return super.onTransact(code, data, reply, flags)
        data.enforceInterface(callback.descriptor)
        deliver(code)
        // The interface is oneway, so there's normally no reply; write one in case a future version isn't.
        reply?.writeNoException()
        return true
    }

    /** Reports the outcome for [code], the first time only. Returns false for a code that isn't the callback's. */
    fun deliver(code: Int): Boolean {
        val outcome = callback.outcomeOf(code) ?: return false
        if (delivered.compareAndSet(false, true)) onOutcome(outcome)
        return true
    }
}
