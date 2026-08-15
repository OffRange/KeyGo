package de.davis.keygo.feature.item.core.presentation.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.davis.keygo.feature.item.core.R

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MenuChip(
    chipText: String,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onModifyClick: (() -> Unit)? = null,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val itemCount = if (onModifyClick != null) 2 else 1

    Box(
        modifier = modifier.wrapContentSize(Alignment.TopStart)
    ) {
        InputChip(
            selected = false,
            onClick = { expanded = !expanded },
            label = { Text(text = chipText) },
            enabled = enabled
        )

        DropdownMenuPopup(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 175.dp)
        ) {
            DropdownMenuGroup(
                shapes = MenuDefaults.groupShape(0, 1),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                onModifyClick?.let { onModify ->
                    DropdownMenuItem(
                        onClick = {
                            expanded = false
                            onModify()
                        },
                        text = { Text(text = stringResource(R.string.edit)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                contentDescription = null
                            )
                        },
                        shape = MenuDefaults.itemShape(0, itemCount).shape,
                    )
                }
                DropdownMenuItem(
                    onClick = {
                        expanded = false
                        onDeleteClick()
                    },
                    text = { Text(text = stringResource(R.string.delete)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                            contentDescription = null
                        )
                    },
                    shape = MenuDefaults.itemShape(itemCount - 1, itemCount).shape,
                )
            }
        }
    }
}
