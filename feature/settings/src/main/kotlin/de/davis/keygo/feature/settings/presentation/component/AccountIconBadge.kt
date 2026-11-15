package de.davis.keygo.feature.settings.presentation.component

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import de.davis.keygo.core.ui.components.IconBadge
import de.davis.keygo.feature.settings.presentation.models.AccountState
import de.davis.keygo.feature.settings.presentation.models.badgeColor

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AccountIconBadge(
    state: AccountState,
    badgeSize: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    IconBadge(
        containerColor = state.badgeColor,
        modifier = modifier,
        shape = MaterialShapes.Cookie6Sided.toShape(),
        size = badgeSize,
    ) {
        if (state is AccountState.Connected)
            Text(
                text = state.username.take(2).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
            )
        else
            Icon(
                imageVector = Icons.Default.Smartphone,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
            )
    }
}