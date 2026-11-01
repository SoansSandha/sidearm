package io.github.soanssandha.sidearm

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.soanssandha.sidearm.data.AppIcons
import io.github.soanssandha.sidearm.ui.MainActions
import io.github.soanssandha.sidearm.ui.MainScreen
import io.github.soanssandha.sidearm.ui.MainViewModel
import io.github.soanssandha.sidearm.ui.theme.SidearmTheme
import kotlin.math.roundToInt

/** The config app's one screen. It runs only while it's open: the app has no services, receivers or jobs. */
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels { MainViewModel.factory(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val icons = AppIcons(packageManager, (ICON_SIZE_DP * resources.displayMetrics.density).roundToInt())
        val version = packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
        val actions = MainActions(
            onEnabledChange = viewModel::setEnabled,
            onOpenPicker = viewModel::loadApps,
            onPick = viewModel::pick,
            onOpenGestureSettings = ::openGestureSettings,
            onOpenSource = { open(Intent(Intent.ACTION_VIEW, SOURCE_URL.toUri())) },
            onMessageShown = viewModel::messageShown,
        )
        setContent {
            SidearmTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                MainScreen(state, icons, version, actions)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may be back from Settings or the Vector manager with something changed.
        viewModel.refresh()
    }

    /** Settings › Gestures › Double tap power button, or the Settings home if this build moved it. */
    private fun openGestureSettings() {
        if (!open(Intent(DOUBLE_TAP_POWER_SETTINGS))) open(Intent(Settings.ACTION_SETTINGS))
    }

    private fun open(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

    private companion object {
        /** The largest icon the screen draws, in dp; smaller ones scale this bitmap down. */
        const val ICON_SIZE_DP = 56

        /** AOSP Settings' exported action for the double-press page (`Settings$DoubleTapPowerSettingsActivity`). */
        const val DOUBLE_TAP_POWER_SETTINGS = "android.settings.action.DOUBLE_TAP_POWER_SETTINGS"
        const val SOURCE_URL = "https://github.com/SoansSandha/sidearm"
    }
}
