package io.github.soanssandha.sidearm.ui

import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.data.DoublePress
import io.github.soanssandha.sidearm.data.LauncherApp
import io.github.soanssandha.sidearm.data.ModuleHost
import io.github.soanssandha.sidearm.data.ModuleLoad
import io.github.soanssandha.sidearm.ui.fixture.FakeAppCatalog
import io.github.soanssandha.sidearm.ui.fixture.FakeDeviceChecks
import io.github.soanssandha.sidearm.ui.fixture.FakeModuleHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private val recorder = LauncherApp(TargetComponent("com.example.recorder", "com.example.recorder.Main"), "Recorder")
    private val notes = LauncherApp(TargetComponent("com.example.notes", "com.example.notes.Main"), "Notes")
    private val armed = Config(enabled = true, target = recorder.component)

    private val dispatcher = StandardTestDispatcher()
    private val hosts = MutableStateFlow<ModuleHost?>(null)
    private val host = FakeModuleHost(armed)
    private val device = FakeDeviceChecks()
    private val catalog = FakeAppCatalog(listOf(notes, recorder))

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MainViewModel(hosts, device, catalog, io = dispatcher, connectTimeoutMillis = TIMEOUT)

    @Test
    fun `reads everything once Vector sends its binder`() = runTest(dispatcher) {
        val viewModel = viewModel()

        hosts.value = host
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(VectorState.Connected(host.info), state.vector)
        assertEquals(true, state.systemInScope)
        assertEquals(ModuleLoad.UP_TO_DATE, state.load)
        assertEquals(armed, state.config)
        assertEquals(recorder, state.targetApp)
        assertEquals(DoublePress.CAMERA, state.doublePress)
        assertEquals(Hero.Active("Recorder"), state.hero)
    }

    @Test
    fun `waits for Vector until the timeout, then says it isn't connected`() = runTest(dispatcher) {
        val viewModel = viewModel()

        advanceTimeBy(TIMEOUT - 1)
        assertEquals(VectorState.Connecting, viewModel.state.value.vector)

        advanceTimeBy(2)
        assertEquals(VectorState.NotConnected, viewModel.state.value.vector)
    }

    @Test
    fun `a binder that arrives after the timeout still connects`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceTimeBy(TIMEOUT + 1)

        hosts.value = host
        advanceUntilIdle()

        assertEquals(VectorState.Connected(host.info), viewModel.state.value.vector)
    }

    @Test
    fun `a binder that dies clears everything read from Vector`() = runTest(dispatcher) {
        val viewModel = viewModel()
        hosts.value = host
        advanceUntilIdle()

        hosts.value = null
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(VectorState.NotConnected, state.vector)
        assertNull(state.config)
        assertNull(state.systemInScope)
        assertNull(state.targetApp)
        assertEquals(DoublePress.CAMERA, state.doublePress)
    }

    @Test
    fun `a binder whose calls fail counts as not connected`() = runTest(dispatcher) {
        val viewModel = viewModel()
        host.dead = true

        hosts.value = host
        advanceUntilIdle()

        assertEquals(VectorState.NotConnected, viewModel.state.value.vector)
    }

    @Test
    fun `stays connected when the framework can't report the load state`() = runTest(dispatcher) {
        val viewModel = viewModel()
        host.loadReportable = false

        hosts.value = host
        advanceUntilIdle()

        assertEquals(VectorState.Connected(host.info), viewModel.state.value.vector)
        assertNull(viewModel.state.value.load)
    }

    @Test
    fun `the switch writes the whole config, keeping dry run, and shows it once stored`() = runTest(dispatcher) {
        host.config = armed.copy(dryRun = true)
        val viewModel = viewModel()
        hosts.value = host
        advanceUntilIdle()

        viewModel.setEnabled(false)
        advanceUntilIdle()

        assertEquals(listOf(armed.copy(enabled = false, dryRun = true)), host.writes)
        assertEquals(false, viewModel.state.value.config?.enabled)
        assertEquals(Hero.Off, viewModel.state.value.hero)
    }

    @Test
    fun `setting the stored value again writes nothing`() = runTest(dispatcher) {
        val viewModel = viewModel()
        hosts.value = host
        advanceUntilIdle()

        viewModel.setEnabled(true)
        advanceUntilIdle()

        assertEquals(emptyList(), host.writes)
    }

    @Test
    fun `a rejected write keeps the stored config and reports it once`() = runTest(dispatcher) {
        val viewModel = viewModel()
        hosts.value = host
        advanceUntilIdle()
        host.acceptWrites = false

        viewModel.setEnabled(false)
        advanceUntilIdle()

        assertEquals(armed, viewModel.state.value.config)
        assertEquals(UserMessage.SAVE_FAILED, viewModel.state.value.message)

        viewModel.messageShown()
        assertNull(viewModel.state.value.message)
    }

    @Test
    fun `a write that throws is reported, and the dead connection shows at once`() = runTest(dispatcher) {
        val viewModel = viewModel()
        hosts.value = host
        advanceUntilIdle()
        host.dead = true

        viewModel.setEnabled(false)
        advanceUntilIdle()

        assertEquals(UserMessage.SAVE_FAILED, viewModel.state.value.message)
        assertEquals(VectorState.NotConnected, viewModel.state.value.vector)
        assertNull(viewModel.state.value.config)
    }

    @Test
    fun `a write starts from the stored config, so a change made over adb survives`() = runTest(dispatcher) {
        val viewModel = viewModel()
        hosts.value = host
        advanceUntilIdle()
        host.config = armed.copy(dryRun = true)

        viewModel.setEnabled(false)
        advanceUntilIdle()

        assertEquals(listOf(armed.copy(enabled = false, dryRun = true)), host.writes)
        assertEquals(armed.copy(enabled = false, dryRun = true), viewModel.state.value.config)
    }

    @Test
    fun `quick taps are stored in order, and the last one wins`() = runTest(dispatcher) {
        val viewModel = viewModel()
        hosts.value = host
        advanceUntilIdle()

        viewModel.setEnabled(false)
        viewModel.setEnabled(true)
        advanceUntilIdle()

        assertEquals(listOf(armed.copy(enabled = false), armed), host.writes)
        assertEquals(armed, viewModel.state.value.config)
    }

    @Test
    fun `a failing package manager hides the app, not the connection`() = runTest(dispatcher) {
        catalog.broken = true
        val viewModel = viewModel()

        hosts.value = host
        advanceUntilIdle()

        assertEquals(VectorState.Connected(host.info), viewModel.state.value.vector)
        assertEquals(armed, viewModel.state.value.config)
        assertNull(viewModel.state.value.targetApp)
    }

    @Test
    fun `picking an app stores it and shows its name`() = runTest(dispatcher) {
        val viewModel = viewModel()
        hosts.value = host
        advanceUntilIdle()

        viewModel.pick(notes)
        advanceUntilIdle()

        assertEquals(armed.copy(target = notes.component), host.config)
        assertEquals(notes, viewModel.state.value.targetApp)
        assertEquals(Hero.Active("Notes"), viewModel.state.value.hero)
    }

    @Test
    fun `nothing is written before Vector connects`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.setEnabled(false)
        viewModel.pick(notes)
        advanceUntilIdle()

        assertEquals(emptyList(), host.writes)
    }

    @Test
    fun `refresh picks up settings changed elsewhere`() = runTest(dispatcher) {
        val viewModel = viewModel()
        hosts.value = host
        advanceUntilIdle()
        device.doublePress = DoublePress.WALLET
        host.inScope = false

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(DoublePress.WALLET, viewModel.state.value.doublePress)
        assertEquals(false, viewModel.state.value.systemInScope)
    }

    @Test
    fun `an uninstalled target reads as gone`() = runTest(dispatcher) {
        catalog.apps = listOf(notes)
        val viewModel = viewModel()

        hosts.value = host
        advanceUntilIdle()

        assertNull(viewModel.state.value.targetApp)
        assertEquals(Hero.TargetGone("com.example.recorder"), viewModel.state.value.hero)
    }

    @Test
    fun `loads the apps for the picker on request`() = runTest(dispatcher) {
        val viewModel = viewModel()
        assertNull(viewModel.state.value.apps)

        viewModel.loadApps()
        advanceUntilIdle()

        assertEquals(listOf(notes, recorder), viewModel.state.value.apps)
    }

    @Test
    fun `a failed app list shows as empty rather than loading forever`() = runTest(dispatcher) {
        catalog.broken = true
        val viewModel = viewModel()

        viewModel.loadApps()
        advanceUntilIdle()

        assertEquals(emptyList(), viewModel.state.value.apps)
    }

    private companion object {
        const val TIMEOUT = 3_000L
    }
}
