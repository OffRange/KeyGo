package de.davis.keygo.feature.item.create.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MultiSupportingLineItem(
    headlineContent: @Composable () -> Unit,
    supportingContent: @Composable ColumnScope.() -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),

        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        leadingContent?.let {
            Box {
                leadingContent()
            }
        }
        Column(
            modifier = Modifier.weight(1f)
        ) {
            val headlineTextStyle = LocalTextStyle.current.merge(MaterialTheme.typography.bodyLarge)
            CompositionLocalProvider(
                LocalTextStyle provides headlineTextStyle,
                LocalContentColor provides MaterialTheme.colorScheme.onSurface
            ) {
                headlineContent()
            }

            val supportingTextStyle =
                LocalTextStyle.current.merge(MaterialTheme.typography.bodyMedium)
            CompositionLocalProvider(
                LocalTextStyle provides supportingTextStyle,
                LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                supportingContent()
            }
        }
    }
}