package io.github.soanssandha.sidearm.data

import io.github.soanssandha.sidearm.data.DoublePress.Companion.ACTION_CAMERA
import io.github.soanssandha.sidearm.data.DoublePress.Companion.ACTION_WALLET
import io.github.soanssandha.sidearm.data.DoublePress.Companion.MODE_CAMERA
import io.github.soanssandha.sidearm.data.DoublePress.Companion.MODE_DISABLED
import io.github.soanssandha.sidearm.data.DoublePress.Companion.MODE_MULTI_TARGET
import kotlin.test.Test
import kotlin.test.assertEquals

class DoublePressTest {

    private fun multiTarget(enabled: Int? = null, action: Int? = null, defaultAction: Int? = ACTION_CAMERA) =
        DoublePress.resolve(MODE_MULTI_TARGET, enabled, action, defaultAction, cameraDisabled = null)

    @Test
    fun `multi-target mode follows the chosen action`() {
        assertEquals(DoublePress.CAMERA, multiTarget(enabled = 1, action = ACTION_CAMERA))
        assertEquals(DoublePress.WALLET, multiTarget(enabled = 1, action = ACTION_WALLET))
    }

    @Test
    fun `multi-target mode is on by default and falls back to the device's default action`() {
        assertEquals(DoublePress.CAMERA, multiTarget())
        assertEquals(DoublePress.WALLET, multiTarget(defaultAction = ACTION_WALLET))
    }

    @Test
    fun `multi-target mode is off when its switch is off, whatever the action`() {
        assertEquals(DoublePress.OFF, multiTarget(enabled = 0, action = ACTION_CAMERA))
    }

    @Test
    fun `multi-target mode with an action this build doesn't know is unknown`() {
        assertEquals(DoublePress.UNKNOWN, multiTarget(enabled = 1, action = 7))
        assertEquals(DoublePress.UNKNOWN, multiTarget(defaultAction = null))
    }

    @Test
    fun `camera-only mode reads the old disable flag, which defaults to enabled`() {
        assertEquals(DoublePress.CAMERA, DoublePress.resolve(MODE_CAMERA, null, null, null, cameraDisabled = null))
        assertEquals(DoublePress.CAMERA, DoublePress.resolve(MODE_CAMERA, null, null, null, cameraDisabled = 0))
        assertEquals(DoublePress.OFF, DoublePress.resolve(MODE_CAMERA, null, null, null, cameraDisabled = 1))
    }

    @Test
    fun `camera-only mode ignores the multi-target settings`() {
        assertEquals(DoublePress.CAMERA, DoublePress.resolve(MODE_CAMERA, 1, ACTION_WALLET, ACTION_WALLET, 0))
    }

    @Test
    fun `disabled mode is off, and an unreadable mode is unknown`() {
        assertEquals(DoublePress.OFF, DoublePress.resolve(MODE_DISABLED, 1, ACTION_CAMERA, ACTION_CAMERA, 0))
        assertEquals(DoublePress.UNKNOWN, DoublePress.resolve(null, 1, ACTION_CAMERA, ACTION_CAMERA, 0))
    }
}
