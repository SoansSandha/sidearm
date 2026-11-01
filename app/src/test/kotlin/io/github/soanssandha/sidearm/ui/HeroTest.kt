package io.github.soanssandha.sidearm.ui

import io.github.soanssandha.sidearm.core.config.Config
import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.data.BuildCheck
import io.github.soanssandha.sidearm.data.DoublePress
import io.github.soanssandha.sidearm.data.FrameworkInfo
import io.github.soanssandha.sidearm.data.LauncherApp
import io.github.soanssandha.sidearm.data.ModuleLoad
import kotlin.test.Test
import kotlin.test.assertEquals

class HeroTest {

    private val target = TargetComponent("com.example.app", "com.example.app.Main")
    private val app = LauncherApp(target, "Example")

    /** Everything in order: the state a working setup reports. */
    private val active = MainUiState(
        build = BuildCheck(id = "CP3A.261005.005", tested = true),
        vector = VectorState.Connected(FrameworkInfo("Vector", "2.2", 102)),
        systemInScope = true,
        load = ModuleLoad.UP_TO_DATE,
        config = Config(enabled = true, target = target),
        targetApp = app,
        doublePress = DoublePress.CAMERA,
    )

    @Test
    fun `a working setup is active and names the app`() {
        assertEquals(Hero.Active("Example"), Hero.of(active))
    }

    @Test
    fun `waits for Vector, then says it isn't connected`() {
        assertEquals(Hero.Connecting, Hero.of(active.copy(vector = VectorState.Connecting)))
        assertEquals(Hero.NotConnected, Hero.of(active.copy(vector = VectorState.NotConnected)))
    }

    @Test
    fun `is still connecting until the config has been read`() {
        assertEquals(Hero.Connecting, Hero.of(active.copy(config = null)))
    }

    @Test
    fun `off wins over every setup problem, which the checks still list`() {
        val broken = active.copy(
            config = active.config?.copy(enabled = false),
            systemInScope = false,
            doublePress = DoublePress.WALLET,
        )

        assertEquals(Hero.Off, Hero.of(broken))
    }

    @Test
    fun `scope comes before load state`() {
        assertEquals(Hero.NotInScope, Hero.of(active.copy(systemInScope = false, load = ModuleLoad.NOT_RUNNING)))
    }

    @Test
    fun `maps each bad load state, and lets a reload or an unknown state through`() {
        assertEquals(Hero.NotLoaded, Hero.of(active.copy(load = ModuleLoad.NOT_RUNNING)))
        assertEquals(Hero.Outdated, Hero.of(active.copy(load = ModuleLoad.STALE)))
        assertEquals(Hero.LoadFailed, Hero.of(active.copy(load = ModuleLoad.FAILED)))
        assertEquals(Hero.Active("Example"), Hero.of(active.copy(load = ModuleLoad.RELOADING)))
        assertEquals(Hero.Active("Example"), Hero.of(active.copy(load = null)))
    }

    @Test
    fun `asks for an app when none is chosen`() {
        val noTarget = active.copy(config = active.config?.copy(target = null), targetApp = null)

        assertEquals(Hero.NoTarget, Hero.of(noTarget))
    }

    @Test
    fun `names the package of an app that's gone`() {
        assertEquals(Hero.TargetGone("com.example.app"), Hero.of(active.copy(targetApp = null)))
    }

    @Test
    fun `pauses when double-press opens Wallet or is off`() {
        assertEquals(
            Hero.DoublePressNotCamera(DoublePress.WALLET),
            Hero.of(active.copy(doublePress = DoublePress.WALLET)),
        )
        assertEquals(Hero.DoublePressNotCamera(DoublePress.OFF), Hero.of(active.copy(doublePress = DoublePress.OFF)))
    }

    @Test
    fun `an unreadable double-press setting doesn't block, since stock may still be on camera`() {
        assertEquals(Hero.Active("Example"), Hero.of(active.copy(doublePress = DoublePress.UNKNOWN)))
    }

    @Test
    fun `a dry run says so instead of active`() {
        assertEquals(Hero.DryRun("Example"), Hero.of(active.copy(config = active.config?.copy(dryRun = true))))
    }
}
