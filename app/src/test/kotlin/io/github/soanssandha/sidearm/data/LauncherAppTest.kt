package io.github.soanssandha.sidearm.data

import io.github.soanssandha.sidearm.core.config.TargetComponent
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LauncherAppTest {

    private val recorder = LauncherApp(
        TargetComponent("com.google.android.apps.recorder", "com.google.android.apps.recorder.ui.MainActivity"),
        "Recorder",
    )

    @Test
    fun `matches its name and package, ignoring case and surrounding spaces`() {
        assertTrue(recorder.matches("rec"))
        assertTrue(recorder.matches("  RECORDER "))
        assertTrue(recorder.matches("apps.recorder"))
    }

    @Test
    fun `an empty search shows everything`() {
        assertTrue(recorder.matches(""))
        assertTrue(recorder.matches("   "))
    }

    @Test
    fun `doesn't match the activity class or unrelated text`() {
        assertFalse(recorder.matches("MainActivity"))
        assertFalse(recorder.matches("camera"))
    }
}
