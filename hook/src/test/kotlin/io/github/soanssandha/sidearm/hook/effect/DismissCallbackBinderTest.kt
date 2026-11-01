package io.github.soanssandha.sidearm.hook.effect

import io.github.soanssandha.sidearm.hook.target.KeyguardDismissCallbackTargets
import io.github.soanssandha.sidearm.hook.target.KeyguardDismissCallbackTargets.Outcome
import io.github.soanssandha.sidearm.hook.target.fixture.KeyguardDismissCallbackFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DismissCallbackBinderTest {

    private val targets = KeyguardDismissCallbackTargets.resolve(
        callbackInterface = KeyguardDismissCallbackFixtures.IKeyguardDismissCallback::class.java,
        stub = KeyguardDismissCallbackFixtures.Stub::class.java,
    ).getOrThrow()

    private val outcomes = mutableListOf<Outcome>()
    private val binder = DismissCallbackBinder(targets, outcomes::add)

    @Test
    fun `reports the outcome of the first call only`() {
        assertTrue(binder.deliver(12))
        assertTrue(binder.deliver(13))

        assertEquals(listOf(Outcome.SUCCEEDED), outcomes)
    }

    @Test
    fun `ignores codes that aren't the callback's`() {
        assertFalse(binder.deliver(1))

        assertEquals(emptyList(), outcomes)
    }
}
