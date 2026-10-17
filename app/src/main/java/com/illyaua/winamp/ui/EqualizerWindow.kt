package com.illyaua.winamp.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.illyaua.winamp.player.PlayerEngine
import kotlin.math.roundToInt

@Composable
fun EqualizerWindow(engine: PlayerEngine, modifier: Modifier = Modifier) {
    val state by engine.state.collectAsStateWithLifecycle()
    val eq = state.eq

    Box(modifier.windowBody(Skin.WIDTH, Skin.EQ_H)) {
        TitleBar("WINAMP EQUALIZER", Modifier.at(0, 0, Skin.WIDTH, Skin.TITLE_H))

        if (!eq.available) {
            LcdText(
                "Equalizer is not available on this device",
                Skin.LcdLabel,
                Modifier.at(14, 54, 250, 10),
                7f,
            )
            return@Box
        }

        BevelButton(Modifier.at(12, 20, 28, 12), { engine.setEqEnabled(!eq.enabled) }, label = "ON", active = eq.enabled)
        BevelButton(Modifier.at(42, 20, 28, 12), engine::resetEq, label = "FLAT")
        BevelButton(Modifier.at(12, 34, 58, 12), engine::nextEqPreset, label = "PRESETS")

        // Response curve through the band levels.
        Box(
            Modifier
                .at(80, 19, 183, 27)
                .drawBehind {
                    panel(0f, 0f, 183f, 27f, raised = false, fill = Skin.LcdBackground)
                    val count = eq.levels.size
                    if (count < 2) return@drawBehind
                    val u = density
                    val span = (eq.maxLevel - eq.minLevel).toFloat()
                    val path = Path()
                    for (i in 0 until count) {
                        val x = (4f + 175f * i / (count - 1)) * u
                        val norm = (eq.levels[i] - eq.minLevel) / span
                        val y = (3f + 21f * (1f - norm)) * u
                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    fillBox(Skin.LcdDim, 2f, 13f, 179f, 1f)
                    drawPath(path, Skin.LcdGreen, style = Stroke(width = u))
                }
        )

        // dB scale on the left.
        val topDb = eq.maxLevel / 100
        val bottomDb = eq.minLevel / 100
        LcdText("+${topDb}dB", Skin.LcdLabel, Modifier.at(12, 50, 36, 8), 6f)
        LcdText("0dB", Skin.LcdLabel, Modifier.at(12, 72, 36, 8), 6f)
        LcdText("${bottomDb}dB", Skin.LcdLabel, Modifier.at(12, 94, 36, 8), 6f)

        // One vertical slider per hardware band.
        val count = eq.levels.size
        if (count > 0) {
            val left = 62f
            val right = 266f
            val cell = (right - left) / count
            for (band in 0 until count) {
                val x = left + cell * band + (cell - 14f) / 2f
                val span = (eq.maxLevel - eq.minLevel).toFloat()
                ClassicSlider(
                    value = (eq.levels[band] - eq.minLevel) / span,
                    onValueChange = { v -> engine.setEqBand(band, (eq.minLevel + v * span).roundToInt()) },
                    modifier = Modifier.at(x, 50, 14, 52),
                    vertical = true,
                    thumbLength = 9f,
                )
                val hz = eq.centerFreqsHz.getOrNull(band) ?: 0
                val label = if (hz >= 1000) "${(hz / 1000f).roundToInt()}k" else "$hz"
                LcdText(label, Skin.LcdLabel, Modifier.at(x - 4f, 104, cell + 6f, 8), 6f)
            }
        }
    }
}
