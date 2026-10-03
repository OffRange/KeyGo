package de.davis.keygo.feature.password_health.presentation.component

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun CountRow(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

@Composable
internal fun CountText(@StringRes label: Int, count: Int) {
    val text = stringResource(label, count)
    val locale = LocalConfiguration.current.locales[0]

    val styled = remember(text, count, locale) {
        // stringResource does its format by taking the first locale so we do it too
        val number = "%d".format(locale, count)
        val start = text.indexOf(number)

        buildAnnotatedString {
            append(text)
            if (start >= 0) addStyle(
                style = SpanStyle(fontWeight = FontWeight.SemiBold),
                start = start,
                end = start + number.length,
            )
        }
    }

    Text(
        text = styled,
        style = MaterialTheme.typography.bodySmall,
    )
}
