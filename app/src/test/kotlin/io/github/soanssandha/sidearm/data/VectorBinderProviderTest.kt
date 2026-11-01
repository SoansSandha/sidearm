package io.github.soanssandha.sidearm.data

import android.os.Process
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VectorBinderProviderTest {

    @Test
    fun `trusts the system uid, which Vector's daemon runs as, and root`() {
        assertTrue(VectorBinderProvider.isTrustedCaller(Process.SYSTEM_UID))
        assertTrue(VectorBinderProvider.isTrustedCaller(Process.ROOT_UID))
    }

    @Test
    fun `refuses the shell and every app`() {
        assertFalse(VectorBinderProvider.isTrustedCaller(Process.SHELL_UID))
        assertFalse(VectorBinderProvider.isTrustedCaller(Process.FIRST_APPLICATION_UID))
        assertFalse(VectorBinderProvider.isTrustedCaller(Process.LAST_APPLICATION_UID))
        // The same app id in a second user.
        assertFalse(VectorBinderProvider.isTrustedCaller(1_010_123))
    }
}
