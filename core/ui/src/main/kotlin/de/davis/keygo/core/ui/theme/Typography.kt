package de.davis.keygo.core.ui.theme

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily

val KeyGoTypography = Typography()

private val Secret = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontFeatureSettings = "tnum",
)

/**
 * The ambient text style with secret typography applied: monospace so lookalike characters stay
 * apart, tabular figures so digits keep their column while a value scrolls.
 *
 * Use it for anything the user reads character by character, such as passwords, card numbers, CVVs
 * and TOTP codes.
 *
 * [FontFamily.Monospace] resolves to whatever font the device ships under that alias. That is
 * usually Roboto Mono, but it varies by OEM and none of them guarantee a slashed zero. Bundling a
 * font to make that deterministic is a change to [Secret] alone.
 */
val secretTextStyle: TextStyle
    @Composable get() = LocalTextStyle.current.merge(Secret)
