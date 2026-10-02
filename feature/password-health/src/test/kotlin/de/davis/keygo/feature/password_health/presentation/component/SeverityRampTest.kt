package de.davis.keygo.feature.password_health.presentation.component

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.luminance
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.test.Test
import kotlin.test.assertTrue

class SeverityRampTest {

    // errorContainer, tertiaryContainer and surfaceContainerHighest of tonal-spot schemes for six
    // wallpapers, light and dark, under both the 2021 and the 2025 spec.
    private val containers = listOf(
        0xFF1F4D52, 0xFF21281F, 0xFF23252E, 0xFF2A2519, 0xFF2C232A, 0xFF2F4E34, 0xFF302221,
        0xFF30231C, 0xFF323630, 0xFF33343A, 0xFF38342B, 0xFF3A3337, 0xFF3D3231, 0xFF3D332D,
        0xFF49491E, 0xFF584419, 0xFF5A3D58, 0xFF663C2F, 0xFF85230A, 0xFF871C34, 0xFF871F21,
        0xFF93000A, 0xFFBCEBF0, 0xFFC8ECC9, 0xFFDEE5D8, 0xFFDFCCFD, 0xFFE0E4DB, 0xFFE1E2ED,
        0xFFE3E2E9, 0xFFE8E5AC, 0xFFE9CDFD, 0xFFEAE2D4, 0xFFECDFE5, 0xFFECE1CE, 0xFFEDEFAC,
        0xFFEEDEE8, 0xFFF0DFD7, 0xFFF1DEDC, 0xFFF5DED2, 0xFFF6DDDA, 0xFFF97386, 0xFFF9C98E,
        0xFFFA746F, 0xFFFAE8A2, 0xFFFCFEB9, 0xFFFD795A, 0xFFFEC392, 0xFFFEDEA6, 0xFFFFD6F8,
        0xFFFFDAD6, 0xFFFFDBD0,
    ).map { Color(it) }

    @Test
    fun everyStepReachesItsContrastOnEveryCard() {
        containers.forEach { container ->
            val ramp = severityRamp(container)
            listOf(
                ramp.critical to CriticalContrast,
                ramp.high to HighContrast,
                ramp.medium to MediumContrast,
            ).forEach { (color, target) ->
                val ratio = contrast(color, container)
                assertTrue(ratio >= target, "$color on $container is $ratio, needs $target")
            }
        }
    }

    @Test
    fun anAccentTintsANeutralCardAndStillReachesItsContrast() {
        val cards = listOf(0xFFF0EAF1, 0xFF2B292D).map { Color(it) }
        val accents = listOf(0xFF7D5260, 0xFFEFB8C8, 0xFF625B71, 0xFF006A6A).map { Color(it) }
        cards.forEach { card ->
            accents.forEach { accent ->
                val ramp = severityRamp(card, accent)
                listOf(
                    ramp.critical to CriticalContrast,
                    ramp.high to HighContrast,
                    ramp.medium to MediumContrast,
                ).forEach { (color, target) ->
                    val ratio = contrast(color, card)
                    assertTrue(ratio >= target, "$color on $card is $ratio, needs $target")
                    val drift = hueDistance(hue(color), hue(accent))
                    assertTrue(drift < 10.0, "$color drifted $drift degrees from $accent")
                }
            }
        }
    }

    @Test
    fun criticalStandsOutMostAndMediumLeast() {
        containers.forEach { container ->
            val ramp = severityRamp(container)
            val critical = contrast(ramp.critical, container)
            val high = contrast(ramp.high, container)
            val medium = contrast(ramp.medium, container)
            assertTrue(critical > high && high > medium, "ramp on $container is out of order")
        }
    }

    @Test
    fun criticalIsTheMostVividAndMediumTheCalmest() {
        containers.forEach { container ->
            val ramp = severityRamp(container)
            assertTrue(
                ramp.critical.chroma() > ramp.high.chroma() &&
                        ramp.high.chroma() > ramp.medium.chroma(),
                "vividness on $container is out of order",
            )
        }
    }

    @Test
    fun aNeutralCardKeepsTheRampMuted() {
        val ramp = severityRamp(Color(0xFFECDFE5))
        assertTrue(ramp.critical.chroma() < 40f, "${ramp.critical} is too saturated")
    }

    @Test
    fun aGreyCardGetsAGreyRamp() {
        val ramp = severityRamp(Color(0xFFEBEBEB))
        listOf(ramp.critical, ramp.high, ramp.medium).forEach {
            assertTrue(it.chroma() < 2f, "$it is tinted")
        }
    }

    @Test
    fun theRampKeepsTheCardsHue() {
        listOf(0xFFFFDAD6, 0xFF93000A, 0xFFDFCCFD, 0xFFC8ECC9).map { Color(it) }.forEach { card ->
            val ramp = severityRamp(card)
            listOf(ramp.critical, ramp.high, ramp.medium).forEach { color ->
                val drift = hueDistance(hue(color), hue(card))
                assertTrue(drift < 10.0, "$color drifted $drift degrees from $card")
            }
        }
    }

    private fun contrast(a: Color, b: Color): Float {
        val la = a.luminance() + 0.05f
        val lb = b.luminance() + 0.05f
        return max(la, lb) / min(la, lb)
    }

    private fun hue(color: Color): Double {
        val lab = color.convert(ColorSpaces.CieLab)
        return atan2(lab.blue.toDouble(), lab.green.toDouble()) * 180 / PI
    }

    private fun hueDistance(a: Double, b: Double): Double {
        val d = abs(a - b) % 360
        return min(d, 360 - d)
    }
}
