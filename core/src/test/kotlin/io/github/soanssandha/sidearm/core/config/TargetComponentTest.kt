package io.github.soanssandha.sidearm.core.config

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TargetComponentTest {

    @Test
    fun `parses a flattened component name`() {
        assertEquals(
            TargetComponent("com.example.app", "com.example.app.MainActivity"),
            TargetComponent.parse("com.example.app/com.example.app.MainActivity"),
        )
    }

    @Test
    fun `expands a class name that starts with a dot, like ComponentName does`() {
        assertEquals(
            TargetComponent("com.example.app", "com.example.app.ui.Main"),
            TargetComponent.parse("com.example.app/.ui.Main"),
        )
    }

    @Test
    fun `accepts digits, underscores and inner classes`() {
        assertEquals(
            TargetComponent("org.foo_bar.app2", "org.foo_bar.app2.Outer\$Inner"),
            TargetComponent.parse("org.foo_bar.app2/org.foo_bar.app2.Outer\$Inner"),
        )
    }

    @Test
    fun `accepts non-ASCII letters in class names but not in package names`() {
        assertEquals(
            TargetComponent("com.example.app", "com.example.app.Café"),
            TargetComponent.parse("com.example.app/.Café"),
        )
        assertNull(TargetComponent.parse("com.exämple.app/com.example.app.Main"))
    }

    @Test
    fun `flatten writes the full class name and parses back to the same component`() {
        val component = TargetComponent("com.example.app", "com.example.app.MainActivity")

        assertEquals("com.example.app/com.example.app.MainActivity", component.flatten())
        assertEquals(component, TargetComponent.parse(component.flatten()))
    }

    @Test
    fun `rejects malformed strings`() {
        val malformed = listOf(
            "",
            "/",
            "com.example.app",
            "com.example.app/",
            "/com.example.app.Main",
            "app/app.Main",
            "com..example/com.example.Main",
            "1com.example/com.example.Main",
            "com.example.app/1Main",
            "com.example.app/.",
            "com.example.app/com.example.Main/extra",
            " com.example.app/com.example.Main",
            "com.example.app/com.example.Main\n",
        )

        malformed.forEach { assertNull(TargetComponent.parse(it), "should reject '$it'") }
    }

    @Test
    fun `constructor rejects invalid parts`() {
        assertFailsWith<IllegalArgumentException> { TargetComponent("app", "app.Main") }
        assertFailsWith<IllegalArgumentException> { TargetComponent("com.example.app", ".Main") }
    }
}
