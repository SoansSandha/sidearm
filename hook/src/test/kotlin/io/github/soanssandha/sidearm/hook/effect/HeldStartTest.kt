package io.github.soanssandha.sidearm.hook.effect

import io.github.libxposed.api.XposedInterface
import io.github.soanssandha.sidearm.hook.effect.fixture.FakeCallerIdentity
import io.github.soanssandha.sidearm.hook.effect.fixture.RecordingLog
import java.lang.reflect.Method
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class HeldStartTest {

    private val identity = FakeCallerIdentity()
    private val log = RecordingLog()
    private val service = Any()
    private val call = HeldStart.Call(service, arrayOf("com.android.systemui", 7), callerIdentity = 42)

    /** Runs nothing; records each call's receiver, arguments and the caller identity it ran as. */
    private inner class RecordingInvoker(private val result: Any?) : XposedInterface.Invoker<RecordingInvoker, Method> {
        val calls = mutableListOf<Triple<Any?, List<Any?>, Long?>>()

        override fun setType(type: XposedInterface.Invoker.Type) = this

        override fun invoke(thisObject: Any?, vararg args: Any?): Any? {
            calls += Triple(thisObject, args.toList(), identity.current)
            return result
        }

        override fun invokeSpecial(thisObject: Any, vararg args: Any?): Any? = invoke(thisObject, *args)
    }

    private fun heldStart(original: RecordingInvoker) = HeldStart(original, identity, startSuccess = 0, log = log)

    @Test
    fun `replays a call on the original method, as the caller that made it`() {
        val original = RecordingInvoker(result = 0)

        heldStart(original).run(call)

        assertEquals(
            listOf(Triple<Any?, List<Any?>, Long?>(service, listOf("com.android.systemui", 7), 42L)),
            original.calls,
        )
        assertEquals(emptyList(), log.lines)
    }

    @Test
    fun `taking empties it, so a held call runs or is dropped only once`() {
        val held = heldStart(RecordingInvoker(result = 0))
        held.hold(call)

        assertSame(call, held.take())
        assertNull(held.take())
    }

    @Test
    fun `holding a second call replaces the first and says so`() {
        val held = heldStart(RecordingInvoker(result = 0))
        val second = HeldStart.Call(service, arrayOf(), callerIdentity = 43)

        held.hold(call)
        held.hold(second)

        assertSame(second, held.take())
        assertTrue(log.lines.single().startsWith("W: A held camera start was replaced"), "log: ${log.lines}")
    }

    @Test
    fun `a replay that doesn't succeed is logged`() {
        heldStart(RecordingInvoker(result = -91)).run(call)

        assertEquals(listOf("W: Held camera start returned -91"), log.lines)
    }
}
