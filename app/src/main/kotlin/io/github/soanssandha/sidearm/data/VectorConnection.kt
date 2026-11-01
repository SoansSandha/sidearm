package io.github.soanssandha.sidearm.data

import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The process's one listener for Vector's service binder. XposedServiceHelper takes a single listener and hands each
 * binder over only once, so everything in the app process gets the service from here.
 *
 * Vector sends the binder through [VectorBinderProvider] when the process starts, and only while Sidearm is enabled in
 * the Vector manager. The helper keeps a binder that arrives before the first read of [service].
 */
object VectorConnection {
    private val bound = MutableStateFlow<XposedService?>(null)

    /** The bound service, or null until Vector sends it and after it dies. */
    val service: StateFlow<XposedService?> = bound.asStateFlow()

    init {
        XposedServiceHelper.registerListener(
            object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(service: XposedService) {
                    bound.value = service
                }

                override fun onServiceDied(service: XposedService) {
                    bound.update { current -> current.takeUnless { it === service } }
                }
            },
        )
    }
}
