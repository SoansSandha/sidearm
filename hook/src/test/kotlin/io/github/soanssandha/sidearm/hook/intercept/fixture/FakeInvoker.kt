package io.github.soanssandha.sidearm.hook.intercept.fixture

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Method

/** Records each call made through it instead of running a method. */
internal class FakeInvoker : XposedInterface.Invoker<FakeInvoker, Method> {

    val calls = mutableListOf<List<Any?>>()

    override fun setType(type: XposedInterface.Invoker.Type): FakeInvoker = this

    override fun invoke(thisObject: Any?, vararg args: Any?): Any? {
        calls += listOf(thisObject, *args)
        return true
    }

    override fun invokeSpecial(thisObject: Any, vararg args: Any?): Any? = invoke(thisObject, *args)
}
