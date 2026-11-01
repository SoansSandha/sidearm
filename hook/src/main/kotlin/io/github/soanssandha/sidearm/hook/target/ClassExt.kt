package io.github.soanssandha.sidearm.hook.target

import java.lang.reflect.Field
import java.lang.reflect.Method

/** The declared method with exactly these parameter types, made accessible. Throws if it's missing or changed type. */
internal fun Class<*>.declaredMethod(
    name: String,
    expectedReturnType: Class<*>,
    vararg parameterTypes: Class<*>,
): Method {
    val method = getDeclaredMethod(name, *parameterTypes)
    check(method.returnType == expectedReturnType) {
        "$name returns ${method.returnType}, expected $expectedReturnType"
    }
    method.isAccessible = true
    return method
}

/** The declared field, made accessible. Throws if it's missing or of another type. */
internal fun Class<*>.declaredField(name: String, expectedType: Class<*>): Field {
    val field = getDeclaredField(name)
    check(field.type == expectedType) { "$name is ${field.type}, expected $expectedType" }
    field.isAccessible = true
    return field
}
