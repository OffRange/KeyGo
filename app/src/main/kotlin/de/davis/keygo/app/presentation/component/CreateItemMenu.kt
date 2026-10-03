package de.davis.keygo.app.presentation.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import de.davis.keygo.R
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.item.generated.presentation.presentation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreateItemMenu(
    visible: Boolean,
    onItemSelected: (VaultItemType) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    // The open menu draws no scrim and consumes nothing outside its items, so the destination
    // underneath keeps taking taps and can navigate away while the menu is still open. The menu
    // belongs to the shell and outlives that navigation, so a destination that drops the button
    // takes the menu with it.
    LaunchedEffect(visible) {
        if (!visible) expanded = false
    }

    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = Modifier.animateFloatingActionButton(
            visible = visible,
            alignment = Alignment.BottomEnd,
        ),
        button = {
            TooltipBox(
                positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                    if (expanded) TooltipAnchorPosition.Start else TooltipAnchorPosition.Above
                ),
                tooltip = { PlainTooltip { Text(stringResource(R.string.add_element_content_description)) } },
                state = rememberTooltipState(),
            ) {
                ToggleFloatingActionButton(
                    checked = expanded,
                    onCheckedChange = { expanded = it },
                    modifier = Modifier.semantics { traversalIndex = -1f },
                ) {
                    val imageVector by remember {
                        derivedStateOf {
                            if (checkedProgress > 0.5f) Icons.Filled.Close else Icons.Filled.Add
                        }
                    }
                    Icon(
                        painter = rememberVectorPainter(imageVector),
                        contentDescription = null,
                        modifier = Modifier.animateIcon({ checkedProgress }),
                    )
                }
            }
        },
    ) {
        VaultItemType.entries.forEach { type ->
            val (text, icon) = type.presentation
            FloatingActionButtonMenuItem(
                onClick = {
                    expanded = false
                    onItemSelected(type)
                },
                icon = { Icon(imageVector = icon, contentDescription = null) },
                text = { Text(text = text) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal val CreateItemMenuButtonSize = ToggleFloatingActionButtonDefaults.containerSize()(0f)
