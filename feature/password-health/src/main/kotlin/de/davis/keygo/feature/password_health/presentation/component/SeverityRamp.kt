package de.davis.keygo.feature.password_health.presentation.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

internal data class SeverityRamp(val critical: Color, val high: Color, val medium: Color)

internal const val CriticalContrast = 4.5f
internal const val HighContrast = 3.75f
internal const val MediumContrast = 3f

private const val MaxChroma = 72f
private const val HighChroma = 0.75f
private const val MediumChroma = 0.5f
private const val ToneStep = 0.5f

// Every step keeps the accent's hue (the card's own by default), so the ramp matches any dynamic
// scheme. Severity is carried mostly by vividness: critical is the most saturated the hue allows,
// the lower steps fade towards it. Contrast alone cannot do this, because the far end of a dark card
// is near white, where no hue survives. Neutral accents get a muted cap so a faint tint does not
// turn neon.
internal fun severityRamp(container: Color, accent: Color = container): SeverityRamp {
    val lab = accent.convert(ColorSpaces.CieLab)
    val hue = atan2(lab.blue, lab.green)
    val cap = min(hypot(lab.green, lab.blue) * 6f, MaxChroma)

    val background = container.luminance() + 0.05f
    val darker = background / 0.05f >= 1.05f / background

    // Gamut mapping and 8-bit rounding can cost a little contrast, so the result is measured and
    // pushed further out until it holds.
    fun step(ratio: Float, chroma: Float): Color {
        val y = if (darker) background / ratio - 0.05f else ratio * background - 0.05f
        var tone = yToTone(y.coerceIn(0f, 1f))
        var color = labColor(tone, chroma, hue)
        while (contrast(color.luminance() + 0.05f, background) < ratio && tone in 0f..100f) {
            tone += if (darker) -ToneStep else ToneStep
            color = labColor(tone.coerceIn(0f, 100f), chroma, hue)
        }
        return color
    }

    val critical = step(CriticalContrast, cap)
    val criticalChroma = critical.chroma()

    return SeverityRamp(
        critical = critical,
        high = step(HighContrast, criticalChroma * HighChroma),
        medium = step(MediumContrast, criticalChroma * MediumChroma),
    )
}

internal fun Color.chroma(): Float {
    val lab = convert(ColorSpaces.CieLab)
    return hypot(lab.green, lab.blue)
}

private fun contrast(a: Float, b: Float) = max(a, b) / min(a, b)

private fun yToTone(y: Float): Float =
    if (y > 216f / 24389f) 116f * cbrt(y) - 16f
    else y * 24389f / 27f

// Lowers chroma until the color fits sRGB, so clamping never shifts the tone the contrast relies on.
private fun labColor(tone: Float, chroma: Float, hue: Float): Color {
    fun at(c: Float) = Color(tone, c * cos(hue), c * sin(hue), colorSpace = ColorSpaces.CieLab)

    val full = at(chroma)
    if (full.fitsSrgb()) return full.convert(ColorSpaces.Srgb)

    var low = 0f
    var high = chroma
    repeat(12) {
        val mid = (low + high) / 2
        if (at(mid).fitsSrgb()) low = mid
        else high = mid
    }
    return at(low).convert(ColorSpaces.Srgb)
}

private fun Color.fitsSrgb(): Boolean {
    val back = convert(ColorSpaces.Srgb).convert(ColorSpaces.CieLab)
    return abs(back.red - red) < 0.5f &&
            abs(back.green - green) < 0.5f &&
            abs(back.blue - blue) < 0.5f
}
