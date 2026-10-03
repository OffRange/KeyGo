package de.davis.keygo.app.presentation.component

import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteItem
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.app.presentation.AppDestinations
import de.davis.keygo.feature.password_health.R as PasswordHealthR

@Composable
internal fun AppDestinationItems(
    navigationSuiteType: NavigationSuiteType,
    selectedRoute: NavKey?,
    needsAttentionCount: Int,
    onDestinationSelected: (NavKey) -> Unit,
) {
    AppDestinations.entries.forEach { destination ->
        NavigationSuiteItem(
            navigationSuiteType = navigationSuiteType,
            selected = destination.route == selectedRoute,
            onClick = { onDestinationSelected(destination.route) },
            icon = { Icon(imageVector = destination.icon, contentDescription = null) },
            label = {
                val label = stringResource(destination.label)
                Text(
                    text = if (navigationSuiteType == NavigationSuiteType.WideNavigationRailExpanded) label
                    else stringResource(destination.shortLabel),
                    modifier = Modifier.semantics { contentDescription = label },
                    textAlign = TextAlign.Center,
                )
            },
            badge = if (destination == AppDestinations.PASSWORD_HEALTH && needsAttentionCount > 0)
                ({ AttentionBadge(needsAttentionCount) })
            else null,
        )
    }
}

@Composable
private fun AttentionBadge(count: Int) {
    val description = pluralStringResource(
        PasswordHealthR.plurals.password_health_needs_attention,
        count,
        count,
    )
    Badge(modifier = Modifier.semantics { contentDescription = description }) {
        Text(text = if (count > MaxBadgeCount) "$MaxBadgeCount+" else count.toString())
    }
}

private const val MaxBadgeCount = 99
