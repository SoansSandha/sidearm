package io.github.soanssandha.sidearm.hook.intercept.fixture

import io.github.libxposed.api.XposedInterface
import java.lang.reflect.Executable

/** A hooked call whose original returns [stockResult]. Counts how often the hooker ran the original. */
internal class FakeChain(private val thisObject: Any, private val args: List<Any?>, private val stockResult: Any?) :
    XposedInterface.Chain {

    var proceedCalls = 0
        private set

    override fun getExecutable(): Executable = throw UnsupportedOperationException("Not needed by the hookers")

    override fun getThisObject(): Any = thisObject

    override fun getArgs(): List<Any?> = args

    override fun getArg(index: Int): Any? = args[index]

    override fun proceed(): Any? {
        proceedCalls++
        return stockResult
    }

    override fun proceed(args: Array<out Any?>): Any? = proceed()

    override fun proceedWith(thisObject: Any): Any? = proceed()

    override fun proceedWith(thisObject: Any, args: Array<out Any?>): Any? = proceed()
}
