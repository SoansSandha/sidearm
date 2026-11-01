package io.github.soanssandha.sidearm.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import io.github.soanssandha.sidearm.core.config.TargetComponent
import io.github.soanssandha.sidearm.data.AppIcons

/**
 * An app's launcher icon, in the shape the system gives it, loaded in the background. Until it loads, and when there's
 * no app or it's gone, a plain circle holds its place.
 */
@Composable
fun AppIcon(component: TargetComponent?, icons: AppIcons, modifier: Modifier = Modifier) {
    val cached = component?.let { icons.cached(it)?.asImageBitmap() }
    val bitmap by produceState(initialValue = cached, component) {
        value = component?.let { icons.load(it)?.asImageBitmap() }
    }
    val loaded = bitmap
    if (loaded != null) {
        Image(loaded, contentDescription = null, modifier = modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape))
    }
}
