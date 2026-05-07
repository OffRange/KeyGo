package de.davis.keygo.feature.list_screen.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.item.generated.presentation.presentation
import de.davis.keygo.core.ui.components.KeyGoCard
import de.davis.keygo.core.ui.components.KeyGoCardProperties
import de.davis.keygo.core.ui.components.KeyGoSwitch
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.feature.list_screen.R
import de.davis.keygo.feature.list_screen.domain.model.SortDirection
import de.davis.keygo.feature.list_screen.presentation.model.FilterAction
import de.davis.keygo.feature.list_screen.presentation.model.FilterBottomSheetState
import de.davis.keygo.feature.list_screen.presentation.model.FilterChipState
import de.davis.keygo.feature.list_screen.presentation.model.ItemSectionState
import de.davis.keygo.feature.list_screen.presentation.model.PasswordSectionState
import de.davis.keygo.core.item.R as CoreItemR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterBottomSheet(
    state: FilterBottomSheetState,
    onAction: (FilterAction) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        FilterBottomSheetContent(
            state = state,
            onAction = onAction,
        )
    }
}

@Composable
private fun FilterBottomSheetContent(
    state: FilterBottomSheetState,
    onAction: (FilterAction) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        stickyHeader(key = "filter_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.filter),
                    style = MaterialTheme.typography.titleLarge,
                )

                TextButton(
                    onClick = { onAction(FilterAction.ClearFilters) },
                    enabled = !state.isDefault,
                ) {
                    Text(text = stringResource(R.string.reset))
                }
            }
        }

        item(key = "sort") {
            SortSection(
                currentDirection = state.sortDirection,
                onDirectionChanged = { onAction(FilterAction.SortDirectionChanged(it)) },
                modifier = Modifier.animateItem(),
            )
        }

        if (state.itemSection != null) {
            item(key = "items") {
                ItemSection(
                    state = state.itemSection,
                    onAction = onAction,
                    modifier = Modifier.animateItem(),
                )
            }
        }

        if (state.passwordSection != null) {
            item(key = "passwords") {
                PasswordSection(
                    state = state.passwordSection,
                    onScoreToggled = { onAction(FilterAction.ScoreToggled(it)) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ItemSection(
    state: ItemSectionState,
    onAction: (FilterAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = DefaultHorizontalArrangement,
    ) {
        SectionHeader(
            icon = Icons.Default.Category,
            title = stringResource(R.string.item),
        )

        if (state.showPinnedSwitch) {
            OutlinedCard {
                KeyGoSwitch(
                    checked = state.onlyPinnedChecked,
                    onCheckedChange = { onAction(FilterAction.ShowOnlyPinnedToggled) },
                    shapes = ListItemDefaults.shapes(
                        shape = CardDefaults.outlinedShape,
                        pressedShape = CardDefaults.outlinedShape,
                        draggedShape = CardDefaults.outlinedShape,
                        focusedShape = CardDefaults.outlinedShape,
                        hoveredShape = CardDefaults.outlinedShape,
                        selectedShape = CardDefaults.outlinedShape,
                    )
                ) {
                    Text(text = stringResource(R.string.only_pinned_items))
                }
            }
        }

        if (state.itemTypeChips.isNotEmpty()) {
            KeyGoCard(
                title = {
                    Text(text = stringResource(R.string.item_type))
                },
                properties = KeyGoCardProperties.outlined(),
            ) {
                FlowRow(horizontalArrangement = DefaultHorizontalArrangement) {
                    state.itemTypeChips.forEach { chip ->
                        FilterChip(
                            selected = chip.selected,
                            onClick = { onAction(FilterAction.ItemTypeToggled(chip.value)) },
                            label = {
                                Text(text = chip.value.presentation.first)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = chip.value.presentation.second,
                                    contentDescription = null,
                                )
                            },
                        )
                    }
                }
            }
        }

        if (state.labelChips.isNotEmpty()) {
            KeyGoCard(
                title = {
                    Text(text = stringResource(R.string.labels))
                },
                properties = KeyGoCardProperties.outlined(),
            ) {
                FlowRow(horizontalArrangement = DefaultHorizontalArrangement) {
                    state.labelChips.forEach { chip ->
                        FilterChip(
                            selected = chip.selected,
                            onClick = { onAction(FilterAction.LabelToggled(chip.value)) },
                            label = {
                                Text(text = chip.value)
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SortSection(
    currentDirection: SortDirection,
    onDirectionChanged: (SortDirection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val directions = SortDirection.entries

    KeyGoCard(
        title = {
            Text(text = stringResource(R.string.sort_order))
        },
        modifier = modifier,
        properties = KeyGoCardProperties.outlined(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        ) {
            directions.forEachIndexed { index, direction ->
                ToggleButton(
                    checked = currentDirection == direction,
                    onCheckedChange = { onDirectionChanged(direction) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
                    shapes = when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        else -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    },
                ) {
                    Icon(
                        direction.icon(),
                        contentDescription = null,
                    )
                    Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    Text(direction.label())
                }
            }
        }
    }
}

@Composable
private fun PasswordSection(
    state: PasswordSectionState,
    onScoreToggled: (Login.Score) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = DefaultHorizontalArrangement,
    ) {
        SectionHeader(
            icon = Icons.Default.Password,
            title = stringResource(CoreItemR.string.password),
        )

        KeyGoCard(
            title = {
                Text(text = stringResource(R.string.password_strength))
            },
        ) {
            FlowRow(horizontalArrangement = DefaultHorizontalArrangement) {
                state.scoreChips.forEach { chip ->
                    FilterChip(
                        selected = chip.selected,
                        onClick = { onScoreToggled(chip.value) },
                        label = {
                            Text(text = chip.value.label())
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
) {
    Row(
        horizontalArrangement = DefaultHorizontalArrangement,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            modifier = Modifier.size(20.dp),
            contentDescription = null,
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
        )
    }
}

@Composable
private fun Login.Score.label(): String = when (this) {
    Login.Score.None -> ""
    Login.Score.Ridiculous -> stringResource(CoreItemR.string.password_strength_ridiculous)
    Login.Score.Weak -> stringResource(CoreItemR.string.password_strength_weak)
    Login.Score.Moderate -> stringResource(CoreItemR.string.password_strength_moderate)
    Login.Score.Strong -> stringResource(CoreItemR.string.password_strength_strong)
    Login.Score.Excellent -> stringResource(CoreItemR.string.password_strength_excellent)
}

@Composable
private fun SortDirection.label(): String = when (this) {
    SortDirection.Ascending -> stringResource(R.string.ascending)
    SortDirection.Descending -> stringResource(R.string.descending)
}

@Composable
private fun SortDirection.icon(): ImageVector = when (this) {
    SortDirection.Ascending -> Icons.Default.ArrowUpward
    SortDirection.Descending -> Icons.Default.ArrowDownward
}

private val DefaultHorizontalArrangement
    get() = Arrangement.spacedBy(8.dp)

@Preview
@Composable
private fun FilterBottomSheetContentPreview() {
    KeyGoTheme {
        Surface {
            FilterBottomSheetContent(
                state = FilterBottomSheetState(
                    sortDirection = SortDirection.Ascending,
                    itemSection = ItemSectionState(
                        showPinnedSwitch = true,
                        onlyPinnedChecked = true,
                        itemTypeChips = VaultItemType.entries.map { type ->
                            FilterChipState(value = type, selected = false)
                        },
                        labelChips = listOf(
                            FilterChipState(value = "Label1", selected = false),
                            FilterChipState(value = "Label2", selected = true),
                        ),
                    ),
                    passwordSection = PasswordSectionState(
                        scoreChips = listOf(
                            FilterChipState(value = Login.Score.Excellent, selected = false),
                            FilterChipState(value = Login.Score.Strong, selected = false),
                            FilterChipState(value = Login.Score.Moderate, selected = true),
                            FilterChipState(value = Login.Score.Weak, selected = true),
                            FilterChipState(value = Login.Score.Ridiculous, selected = false),
                        ),
                    ),
                    isDefault = false,
                ),
                onAction = {},
            )
        }
    }
}
