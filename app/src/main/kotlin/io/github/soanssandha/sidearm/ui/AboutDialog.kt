package io.github.soanssandha.sidearm.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.soanssandha.sidearm.R

/** The app's version and license, with a link to its source, which the GPL asks us to offer. */
@Composable
fun AboutDialog(version: String, onOpenSource: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_title, version)) },
        text = { Text(stringResource(R.string.about_body)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = { TextButton(onClick = onOpenSource) { Text(stringResource(R.string.source_code)) } },
    )
}
