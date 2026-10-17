package com.illyaua.winamp.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

// DrawScope.density is the number of pixels per classic pixel, because the root composable overrides LocalDensity.

internal fun DrawScope.fillBox(color: Color, x: Float, y: Float, w: Float, h: Float) {
    val u = density
    drawRect(color, Offset(x * u, y * u), Size(w * u, h * u))
}

/** A 1px bevelled rectangle. Raised looks like a button, lowered like a groove. */
internal fun DrawScope.panel(x: Float, y: Float, w: Float, h: Float, raised: Boolean, fill: Color) {
    fillBox(fill, x, y, w, h)
    val light = if (raised) Skin.BevelLight else Skin.BevelDark
    val dark = if (raised) Skin.BevelDark else Skin.BevelLight
    fillBox(light, x, y, w, 1f)
    fillBox(light, x, y, 1f, h)
    fillBox(dark, x, y + h - 1f, w, 1f)
    fillBox(dark, x + w - 1f, y, 1f, h)
}

internal fun DrawScope.polygon(color: Color, vararg points: Float) {
    val u = density
    val path = Path().apply {
        moveTo(points[0] * u, points[1] * u)
        var i = 2
        while (i < points.size) {
            lineTo(points[i] * u, points[i + 1] * u)
            i += 2
        }
        close()
    }
    drawPath(path, color)
}

// Transport icons, drawn centred in the button.

internal fun DrawScope.iconPrevious(c: Color) {
    val cx = size.width / density / 2f
    val cy = size.height / density / 2f
    fillBox(c, cx - 5f, cy - 4f, 2f, 8f)
    polygon(c, cx + 4f, cy - 4f, cx + 4f, cy + 4f, cx - 3f, cy)
}

internal fun DrawScope.iconPlay(c: Color) {
    val cx = size.width / density / 2f
    val cy = size.height / density / 2f
    polygon(c, cx - 3f, cy - 5f, cx - 3f, cy + 5f, cx + 5f, cy)
}

internal fun DrawScope.iconPause(c: Color) {
    val cx = size.width / density / 2f
    val cy = size.height / density / 2f
    fillBox(c, cx - 4f, cy - 4f, 3f, 8f)
    fillBox(c, cx + 1f, cy - 4f, 3f, 8f)
}

internal fun DrawScope.iconStop(c: Color) {
    val cx = size.width / density / 2f
    val cy = size.height / density / 2f
    fillBox(c, cx - 4f, cy - 4f, 8f, 8f)
}

internal fun DrawScope.iconNext(c: Color) {
    val cx = size.width / density / 2f
    val cy = size.height / density / 2f
    polygon(c, cx - 4f, cy - 4f, cx - 4f, cy + 4f, cx + 3f, cy)
    fillBox(c, cx + 3f, cy - 4f, 2f, 8f)
}

internal fun DrawScope.iconEject(c: Color) {
    val cx = size.width / density / 2f
    val cy = size.height / density / 2f
    polygon(c, cx - 5f, cy + 1f, cx + 5f, cy + 1f, cx, cy - 4f)
    fillBox(c, cx - 5f, cy + 2.5f, 10f, 2f)
}
