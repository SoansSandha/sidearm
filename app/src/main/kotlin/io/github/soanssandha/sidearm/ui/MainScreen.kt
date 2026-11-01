package io.github.soanssandha.sidearm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.soanssandha.sidearm.R
import io.github.soanssandha.sidearm.data.AppIcons

/** The config screen: status, the chosen app, the checks behind the status, and what each press count does. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(state: MainUiState, icons: AppIcons, version: String, actions: MainActions) {
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    var aboutOpen by rememberSaveable { mutableStateOf(false) }
    val openPicker = { pickerOpen = true }
    // Keyed on the sheet, not the tap, so a sheet restored after the process was killed loads its list too.
    LaunchedEffect(pickerOpen) {
        if (pickerOpen) actions.onOpenPicker()
    }
    val snackbarHost = remember { SnackbarHostState() }
    val saveFailed = stringResource(R.string.save_failed)
    LaunchedEffect(state.message) {
        if (state.message == UserMessage.SAVE_FAILED) {
            snackbarHost.showSnackbar(saveFailed)
            actions.onMessageShown()
        }
    }

    Scaffold(
        topBar = { TopBar(onAbout = { aboutOpen = true }) },
        snackbarHost = { SnackbarHost(snackbarHost) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusCard(state, actions.onEnabledChange, onChooseApp = openPicker, actions.onOpenGestureSettings)
            TargetCard(state, icons, onChange = openPicker)
            ChecksCard(state)
            PressMapCard(state)
        }
    }

    if (pickerOpen) {
        AppPickerSheet(
            apps = state.apps,
            selected = state.config?.target,
            icons = icons,
            onPick = actions.onPick,
            onDismiss = { pickerOpen = false },
        )
    }
    if (aboutOpen) {
        AboutDialog(version, onOpenSource = actions.onOpenSource, onDismiss = { aboutOpen = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(onAbout: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    TopAppBar(
        title = { Text(stringResource(R.string.app_name)) },
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.about)) },
                        onClick = {
                            menuOpen = false
                            onAbout()
                        },
                    )
                }
            }
        },
    )
}
