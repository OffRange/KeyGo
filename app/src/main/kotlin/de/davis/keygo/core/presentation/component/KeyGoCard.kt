package de.davis.keygo.core.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Immutable
data class KeyGoCardProp(
    val shape: Shape,
    val colors: CardColors,
    val elevation: CardElevation,
    val border: BorderStroke?,
) {

    companion object {

        @Composable
        fun outlined(containerColor: Color = Color.Unspecified) = KeyGoCardProp(
            shape = CardDefaults.outlinedShape,
            colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
            elevation = CardDefaults.outlinedCardElevation(),
            border = CardDefaults.outlinedCardBorder()
        )

        @Composable
        fun elevated(containerColor: Color = Color.Unspecified) = KeyGoCardProp(
            shape = CardDefaults.elevatedShape,
            colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
            elevation = CardDefaults.elevatedCardElevation(),
            border = null
        )
    }
}

@Composable
fun KeyGoCard(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    prop: KeyGoCardProp = KeyGoCardProp.outlined(),
    leadingItem: @Composable (() -> Unit)? = null,
    trailingItem: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    with(prop) {
        Card(
            modifier = modifier,
            shape = shape,
            colors = colors,
            elevation = elevation,
            border = border,
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                leadingItem?.let {
                    Box(modifier = Modifier.minimumInteractiveComponentSize()) {
                        leadingItem()
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.bodySmall
                    ) {
                        title()
                    }

                    CompositionLocalProvider(
                        LocalTextStyle provides MaterialTheme.typography.bodyLarge
                    ) {
                        content()
                    }
                }

                trailingItem?.let {
                    Box(modifier = Modifier.minimumInteractiveComponentSize()) {
                        trailingItem()
                    }
                }
            }
        }
    }
}