package com.illyaua.winamp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.illyaua.winamp.core.SevenSegment
import com.illyaua.winamp.core.SpectrumFrame

/** Places a child at classic-pixel coordinates inside its parent Box. */
fun Modifier.at(x: Number, y: Number, w: Number, h: Number): Modifier =
    absoluteOffset(x.toFloat().dp, y.toFloat().dp).size(w.toFloat().dp, h.toFloat().dp)

fun lcdStyle(color: Color, size: Float = 7f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = (size + 1f).sp,
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Bold,
)

@Composable
fun TitleBar(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(Skin.WIDTH.dp, Skin.TITLE_H.dp)
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(listOf(Skin.TitleTop, Skin.TitleBottom)),
                    size = size,
                )
                var y = 3f
                while (y < Skin.TITLE_H - 2f) {
                    fillBox(Skin.TitleStripe, 3f, y, Skin.WIDTH - 6f, 1f)
                    y += 2f
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            title,
            style = lcdStyle(Skin.TitleText, 7f),
            modifier = Modifier
                .background(Skin.TitleBottom)
                .padding(horizontal = 5.dp),
        )
    }
}

/** Window body: gradient fill with a raised bevel. */
fun Modifier.windowBody(width: Float, height: Float): Modifier = this
    .size(width.dp, height.dp)
    .drawBehind {
        drawRect(Brush.verticalGradient(listOf(Skin.BodyTop, Skin.BodyBottom)), size = size)
        panel(0f, 0f, width, height, raised = true, fill = Color.Transparent)
    }

@Composable
fun BevelButton(
    modifier: Modifier,
    onClick: () -> Unit,
    label: String? = null,
    active: Boolean = false,
    icon: (DrawScope.(Color) -> Unit)? = null,
) {
    var pressed by remember { mutableStateOf(false) }
    val currentOnClick by rememberUpdatedState(onClick)
    val tint = if (active) Skin.LcdGreen else Skin.Icon
    Box(
        modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        try {
                            tryAwaitRelease()
                        } finally {
                            pressed = false
                        }
                    },
                    onTap = { currentOnClick() },
                )
            }
            .drawBehind {
                val down = pressed
                panel(
                    0f, 0f, size.width / density, size.height / density,
                    raised = !down,
                    fill = if (down) Skin.ButtonDown else Skin.ButtonFace,
                )
                icon?.invoke(this, tint)
            },
        contentAlignment = Alignment.Center,
    ) {
        if (label != null) {
            BasicText(
                label,
                style = lcdStyle(if (active) Skin.LcdGreen else Skin.Label, 6f),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/**
 * Slider with a bevelled thumb, horizontal or vertical. [value] runs 0..1; vertical sliders have 1 at the top.
 * The callbacks fire while dragging and once on release.
 */
@Composable
fun ClassicSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier,
    onValueChangeFinished: () -> Unit = {},
    vertical: Boolean = false,
    thumbLength: Float = 11f,
    fillGradient: Boolean = false,
    enabled: Boolean = true,
) {
    val currentChange by rememberUpdatedState(onValueChange)
    val currentFinished by rememberUpdatedState(onValueChangeFinished)
    Box(
        modifier
            .pointerInput(enabled, vertical) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun toValue(p: Offset): Float {
                        val length = if (vertical) size.height else size.width
                        val pos = if (vertical) p.y else p.x
                        val thumb = thumbLength * density
                        val usable = (length - thumb).coerceAtLeast(1f)
                        val f = ((pos - thumb / 2f) / usable).coerceIn(0f, 1f)
                        return if (vertical) 1f - f else f
                    }
                    down.consume()
                    currentChange(toValue(down.position))
                    drag(down.id) { change ->
                        currentChange(toValue(change.position))
                        change.consume()
                    }
                    currentFinished()
                }
            }
            .drawBehind {
                val w = size.width / density
                val h = size.height / density
                val v = value.coerceIn(0f, 1f)
                val travel = (if (vertical) h else w) - thumbLength
                val offset = (if (vertical) 1f - v else v) * travel

                if (vertical) {
                    panel(w / 2f - 1.5f, 0f, 3f, h, raised = false, fill = Skin.GrooveTop)
                } else {
                    panel(0f, 0f, w, h, raised = false, fill = Skin.GrooveTop)
                    if (fillGradient) {
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(Skin.SpectrumBottom, Skin.SpectrumMid, Skin.SpectrumTop),
                                startX = 0f,
                                endX = size.width,
                            ),
                            topLeft = Offset(density, density),
                            size = Size((offset + thumbLength / 2f) * density, size.height - 2f * density),
                        )
                    }
                }
                if (vertical) {
                    panel(0f, offset, w, thumbLength, raised = true, fill = Skin.ThumbFace)
                } else {
                    panel(offset, 0f, thumbLength, h, raised = true, fill = Skin.ThumbFace)
                }
            }
    )
}

/** Four-digit seven-segment clock, "MMSS", with an optional leading minus for remaining time. */
@Composable
fun LcdClock(digits: String, negative: Boolean, modifier: Modifier) {
    Box(
        modifier.drawBehind {
            if (negative) drawDigit('-', 0f)
            drawDigit(digits[0], 6f)
            drawDigit(digits[1], 17f)
            fillBox(Skin.LcdGreen, 29f, 3f, 2f, 2f)
            fillBox(Skin.LcdGreen, 29f, 8f, 2f, 2f)
            drawDigit(digits[2], 34f)
            drawDigit(digits[3], 45f)
        }
    )
}

private fun DrawScope.drawDigit(ch: Char, x: Float) {
    val mask = SevenSegment.mask(ch)
    val c = Skin.LcdGreen
    val narrow = ch == '-'
    val w = if (narrow) 5f else 9f
    if (mask and SevenSegment.A != 0) fillBox(c, x + 1f, 0f, w - 2f, 2f)
    if (mask and SevenSegment.B != 0) fillBox(c, x + w - 2f, 1f, 2f, 5.5f)
    if (mask and SevenSegment.C != 0) fillBox(c, x + w - 2f, 6.5f, 2f, 5.5f)
    if (mask and SevenSegment.D != 0) fillBox(c, x + 1f, 11f, w - 2f, 2f)
    if (mask and SevenSegment.E != 0) fillBox(c, x, 6.5f, 2f, 5.5f)
    if (mask and SevenSegment.F != 0) fillBox(c, x, 1f, 2f, 5.5f)
    if (mask and SevenSegment.G != 0) fillBox(c, x + 1f, 5.5f, w - 2f, 2f)
}

/** 19 bars with falling peak markers. Reads [frame] while drawing so a new frame does not recompose anything. */
@Composable
fun SpectrumView(frame: State<SpectrumFrame>, modifier: Modifier) {
    Box(
        modifier.drawBehind {
            val u = density
            drawRect(Skin.LcdBackground)
            val brush = Brush.verticalGradient(
                listOf(Skin.SpectrumTop, Skin.SpectrumMid, Skin.SpectrumBottom),
                startY = 0f,
                endY = size.height,
            )
            val current = frame.value
            val rows = size.height / u
            for (i in current.bars.indices) {
                val x = i * 4f * u
                val barRows = (current.bars[i] * rows).toInt().coerceIn(0, rows.toInt())
                if (barRows > 0) {
                    drawRect(brush, Offset(x, size.height - barRows * u), Size(3f * u, barRows * u))
                }
                val peakRows = (current.peaks[i] * rows).toInt().coerceIn(0, rows.toInt())
                if (peakRows > 0) {
                    fillBox(Skin.SpectrumPeak, i * 4f, rows - peakRows, 3f, 1f)
                }
            }
        }
    )
}

@Composable
fun LcdText(text: String, color: Color, modifier: Modifier = Modifier, size: Float = 7f) {
    BasicText(
        text,
        modifier = modifier,
        style = lcdStyle(color, size),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}
