package io.github.soanssandha.sidearm.hook.target

import android.os.Binder
import io.github.soanssandha.sidearm.hook.target.KeyguardDismissCallbackTargets.Outcome
import io.github.soanssandha.sidearm.hook.target.fixture.KeyguardDismissCallbackFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KeyguardDismissCallbackTargetsTest {

    private fun resolve(stub: Class<*> = KeyguardDismissCallbackFixtures.Stub::class.java) =
        KeyguardDismissCallbackTargets.resolve(
            callbackInterface = KeyguardDismissCallbackFixtures.IKeyguardDismissCallback::class.java,
            stub = stub,
        )

    @Test
    fun `reads the descriptor and maps each transaction code to its outcome`() {
        val targets = resolve().getOrThrow()

        assertEquals("com.android.internal.policy.IKeyguardDismissCallback", targets.descriptor)
        assertEquals(Outcome.ERROR, targets.outcomeOf(11))
        assertEquals(Outcome.SUCCEEDED, targets.outcomeOf(12))
        assertEquals(Outcome.CANCELLED, targets.outcomeOf(13))
        assertNull(targets.outcomeOf(1), "not one of the callback's codes")
    }

    @Test
    fun `wraps a binder as the callback interface`() {
        val binder = Binder()

        val callback = resolve().getOrThrow().asInterface(binder)

        assertSame(binder, (callback as KeyguardDismissCallbackFixtures.Proxy).binder)
    }

    @Test
    fun `refuses a build whose stub lost a transaction code`() {
        val error = resolve(KeyguardDismissCallbackFixtures.StubWithoutCancelled::class.java).exceptionOrNull()

        assertTrue(error != null && "TRANSACTION_onDismissCancelled" in error.toString(), "got $error")
    }

    @Test
    fun `refuses, without throwing, when the framework classes aren't there`() {
        assertTrue(KeyguardDismissCallbackTargets.resolve(javaClass.classLoader).isFailure)
    }
}
