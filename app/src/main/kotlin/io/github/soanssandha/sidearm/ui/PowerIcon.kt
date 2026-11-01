package io.github.soanssandha.sidearm.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** A power symbol (the core icon set has none), drawn in strokes so `Icon` tints it like the others. */
internal val PowerIcon: ImageVector = ImageVector.Builder(
    name = "Power",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(
        stroke = SolidColor(Color.Black),
        strokeLineWidth = 2f,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    ) {
        moveTo(12f, 3f)
        lineTo(12f, 11f)
        moveTo(6.3f, 6.8f)
        arcTo(8f, 8f, 0f, isMoreThanHalf = true, isPositiveArc = false, x1 = 17.7f, y1 = 6.8f)
    }
}.build()
