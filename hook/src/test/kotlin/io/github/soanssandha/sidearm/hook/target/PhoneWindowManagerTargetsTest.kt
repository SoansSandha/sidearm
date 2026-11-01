package io.github.soanssandha.sidearm.hook.target

import io.github.soanssandha.sidearm.hook.target.fixture.PhoneWindowManagerFixtures
import io.github.soanssandha.sidearm.hook.target.fixture.PhoneWindowManagerFixtures.KeyguardServiceDelegate
import io.github.soanssandha.sidearm.hook.target.fixture.PhoneWindowManagerFixtures.LocalServices
import io.github.soanssandha.sidearm.hook.target.fixture.PhoneWindowManagerFixtures.PhoneWindowManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PhoneWindowManagerTargetsTest {

    private fun resolve(phoneWindowManager: Class<*>) = PhoneWindowManagerTargets.resolve(
        localServices = LocalServices::class.java,
        policy = PhoneWindowManagerFixtures.WindowManagerPolicy::class.java,
        phoneWindowManager = phoneWindowManager,
        keyguardDelegate = KeyguardServiceDelegate::class.java,
        userHandle = PhoneWindowManagerFixtures.UserHandle::class.java,
    )

    private val targets by lazy { resolve(PhoneWindowManager::class.java).getOrThrow() }

    @AfterTest
    fun clearLocalServices() {
        LocalServices.registered = null
    }

    @Test
    fun `finds the running PhoneWindowManager through LocalServices`() {
        val policy = PhoneWindowManager(KeyguardServiceDelegate(showing = false))

        assertNull(targets.policy(), "nothing is registered before WindowManagerService starts")
        LocalServices.registered = policy
        assertSame(policy, targets.policy())
    }

    @Test
    fun `ignores a policy that isn't PhoneWindowManager`() {
        LocalServices.registered = PhoneWindowManagerFixtures.OtherPolicy()

        assertNull(targets.policy())
    }

    @Test
    fun `reads whether the keyguard is showing, and assumes it is when that can't be told`() {
        assertTrue(targets.isKeyguardShowing(PhoneWindowManager(KeyguardServiceDelegate(showing = true))))
        assertFalse(targets.isKeyguardShowing(PhoneWindowManager(KeyguardServiceDelegate(showing = false))))
        assertTrue(targets.isKeyguardShowing(PhoneWindowManager(mKeyguardDelegate = null)))
    }

    @Test
    fun `refuses a build that changed any target`() {
        val drifted = mapOf(
            PhoneWindowManagerFixtures.WithoutStartActivity::class.java to "startActivityAsUser",
            PhoneWindowManagerFixtures.WithOtherKeyguardDelegate::class.java to "mKeyguardDelegate",
        )

        drifted.forEach { (phoneWindowManager, member) ->
            val error = resolve(phoneWindowManager).exceptionOrNull()
            assertTrue(error != null && member in error.toString(), "expected a refusal over $member, got $error")
        }
    }

    @Test
    fun `refuses, without throwing, when the framework classes aren't there`() {
        assertTrue(PhoneWindowManagerTargets.resolve(javaClass.classLoader).isFailure)
    }
}
