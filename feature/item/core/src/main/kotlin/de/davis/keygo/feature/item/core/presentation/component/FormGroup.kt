package de.davis.keygo.feature.item.core.presentation.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.davis.keygo.core.ui.components.KeyGoCard
import de.davis.keygo.core.ui.components.KeyGoCardProperties

@Composable
internal fun FormGroup(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    KeyGoCard(
        title = {
            Text(text = title)
        },
        modifier = modifier,
        properties = KeyGoCardProperties.elevated(),
        content = content
    )
}