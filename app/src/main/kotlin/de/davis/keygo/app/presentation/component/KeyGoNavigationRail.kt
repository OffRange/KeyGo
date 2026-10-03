package de.davis.keygo.app.presentation.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalWideNavigationRail
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.R
import kotlinx.coroutines.launch
import de.davis.keygo.core.ui.R as CoreUiR

@Composable
internal fun KeyGoNavigationRail(
    modal: Boolean,
    visible: Boolean,
    selectedRoute: NavKey?,
    needsAttentionCount: Int,
    onDestinationSelected: (NavKey) -> Unit,
    onCreateClicked: () -> Unit,
) {
    // Keyed so a window crossing between the two modes starts from the new mode's default.
    val state = key(modal) {
        rememberWideNavigationRailState(
            if (modal) WideNavigationRailValue.Collapsed else WideNavigationRailValue.Expanded
        )
    }
    val scope = rememberCoroutineScope()
    val expanded = state.targetValue == WideNavigationRailValue.Expanded

    LaunchedEffect(state, visible) {
        if (modal && !visible) state.snapTo(WideNavigationRailValue.Collapsed)
    }

    val dismissModal: () -> Unit = {
        if (modal) scope.launch { state.collapse() }
    }

    val header = @Composable {
        RailHeader(
            expanded = expanded,
            onToggle = { scope.launch { state.toggle() } },
            onCreateClicked = {
                dismissModal()
                onCreateClicked()
            },
        )
    }

    val items = @Composable {
        AppDestinationItems(
            navigationSuiteType = if (expanded) NavigationSuiteType.WideNavigationRailExpanded
            else NavigationSuiteType.WideNavigationRailCollapsed,
            selectedRoute = selectedRoute,
            needsAttentionCount = needsAttentionCount,
            onDestinationSelected = { route ->
                dismissModal()
                onDestinationSelected(route)
            },
        )
    }

    if (modal) ModalWideNavigationRail(state = state, header = header, content = items)
    else WideNavigationRail(state = state, header = header, content = items)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RailHeader(
    expanded: Boolean,
    onToggle: () -> Unit,
    onCreateClicked: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val toggleDescription = stringResource(
            if (expanded) R.string.collapse_navigation_content_description
            else R.string.expand_navigation_content_description
        )
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                TooltipAnchorPosition.End
            ),
            tooltip = { PlainTooltip { Text(toggleDescription) } },
            state = rememberTooltipState(),
        ) {
            IconButton(
                onClick = onToggle,
                modifier = Modifier.padding(start = 24.dp),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.AutoMirrored.Filled.MenuOpen
                    else Icons.Filled.Menu,
                    contentDescription = toggleDescription,
                )
            }
        }

        val fabContainerColor = MaterialTheme.colorScheme.tertiaryContainer
        ExtendedFloatingActionButton(
            onClick = onCreateClicked,
            expanded = expanded,
            modifier = Modifier.padding(start = 20.dp),
            containerColor = fabContainerColor,
            contentColor = contentColorFor(fabContainerColor),
            icon = {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_element_content_description),
                )
            },
            text = { Text(text = stringResource(CoreUiR.string.add)) },
        )
    }
}
