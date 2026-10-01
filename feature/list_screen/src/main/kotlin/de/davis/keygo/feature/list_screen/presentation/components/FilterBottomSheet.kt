package de.davis.keygo.feature.list_screen.presentation.components

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberBottomSheetState
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
import de.davis.keygo.core.item.domain.model.CardExpiryStatus
import de.davis.keygo.core.item.domain.model.CredentialType
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.item.generated.presentation.presentation
import de.davis.keygo.core.item.presentation.label
import de.davis.keygo.core.ui.components.KeyGoCard
import de.davis.keygo.core.ui.components.KeyGoCardProperties
import de.davis.keygo.core.ui.components.KeyGoSwitch
import de.davis.keygo.core.ui.theme.KeyGoTheme
import de.davis.keygo.feature.list_screen.R
import de.davis.keygo.feature.list_screen.domain.model.FilterFacet
import de.davis.keygo.feature.list_screen.domain.model.SortDirection
import de.davis.keygo.feature.list_screen.presentation.model.CreditCardSectionState
import de.davis.keygo.feature.list_screen.presentation.model.FilterAction
import de.davis.keygo.feature.list_screen.presentation.model.FilterBottomSheetState
import de.davis.keygo.feature.list_screen.presentation.model.FilterChipState
import de.davis.keygo.feature.list_screen.presentation.model.ItemSectionState
import de.davis.keygo.feature.list_screen.presentation.model.LoginSectionState
import de.davis.keygo.core.item.R as CoreItemR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FilterBottomSheet(
    state: FilterBottomSheetState,
    onAction: (FilterAction) -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
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
            .animateContentSize()
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

        if (state.loginSection != null) {
            item(key = "logins") {
                LoginSection(
                    state = state.loginSection,
                    onAction = onAction,
                    modifier = Modifier.animateItem(),
                )
            }
        }

        if (state.creditCardSection != null) {
            item(key = "credit_cards") {
                CreditCardSection(
                    state = state.creditCardSection,
                    onAction = onAction,
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

        if (state.onlyPinned != null) {
            OutlinedCard {
                KeyGoSwitch(
                    checked = state.onlyPinned.selected,
                    onCheckedChange = {
                        onAction(FilterAction.Toggled(FilterFacet.Pinned, state.onlyPinned.value))
                    },
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
                FacetChips(
                    facet = FilterFacet.ItemTypes,
                    chips = state.itemTypeChips,
                    onAction = onAction,
                    label = { Text(text = it.presentation.first) },
                    leadingIcon = {
                        Icon(imageVector = it.presentation.second, contentDescription = null)
                    },
                )
            }
        }

        if (state.tagChips.isNotEmpty()) {
            KeyGoCard(
                title = {
                    Text(text = stringResource(R.string.tags))
                },
                properties = KeyGoCardProperties.outlined(),
            ) {
                FacetChips(
                    facet = FilterFacet.Tags,
                    chips = state.tagChips,
                    onAction = onAction,
                    label = { Text(text = it.display) },
                )
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
private fun LoginSection(
    state: LoginSectionState,
    onAction: (FilterAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = DefaultHorizontalArrangement,
    ) {
        SectionHeader(
            icon = VaultItemType.Login.presentation.second,
            title = VaultItemType.Login.presentation.first,
        )

        if (state.passwordScoreChips.isNotEmpty()) {
            KeyGoCard(
                title = {
                    Text(text = stringResource(R.string.password_strength))
                },
            ) {
                FacetChips(
                    facet = FilterFacet.PasswordScores,
                    chips = state.passwordScoreChips,
                    onAction = onAction,
                    label = { Text(text = it.label()) },
                )
            }
        }

        if (state.credentialChips.isNotEmpty()) {
            KeyGoCard(
                title = {
                    Text(text = stringResource(R.string.credentials))
                },
            ) {
                FacetChips(
                    facet = FilterFacet.Credentials,
                    chips = state.credentialChips,
                    onAction = onAction,
                    label = { Text(text = it.label()) },
                    leadingIcon = {
                        Icon(imageVector = it.icon(), contentDescription = null)
                    },
                )
            }
        }
    }
}

@Composable
private fun CreditCardSection(
    state: CreditCardSectionState,
    onAction: (FilterAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = DefaultHorizontalArrangement,
    ) {
        SectionHeader(
            icon = VaultItemType.CreditCard.presentation.second,
            title = VaultItemType.CreditCard.presentation.first,
        )

        if (state.expiryStatusChips.isNotEmpty()) {
            KeyGoCard(
                title = {
                    Text(text = stringResource(R.string.expiry_status))
                },
            ) {
                FacetChips(
                    facet = FilterFacet.CardExpiryStatuses,
                    chips = state.expiryStatusChips,
                    onAction = onAction,
                    label = { Text(text = it.label()) },
                )
            }
        }
    }
}

@Composable
private fun <T : Any> FacetChips(
    facet: FilterFacet<T>,
    chips: List<FilterChipState<T>>,
    onAction: (FilterAction) -> Unit,
    label: @Composable (T) -> Unit,
    leadingIcon: (@Composable (T) -> Unit)? = null,
) {
    FlowRow(horizontalArrangement = DefaultHorizontalArrangement) {
        chips.forEach { chip ->
            FilterChip(
                selected = chip.selected,
                onClick = { onAction(FilterAction.Toggled(facet, chip.value)) },
                label = { label(chip.value) },
                leadingIcon = leadingIcon?.let { { it(chip.value) } },
            )
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
private fun PasswordScore.label(): String = when (this) {
    PasswordScore.None -> ""
    PasswordScore.Ridiculous -> stringResource(CoreItemR.string.password_strength_ridiculous)
    PasswordScore.Weak -> stringResource(CoreItemR.string.password_strength_weak)
    PasswordScore.Moderate -> stringResource(CoreItemR.string.password_strength_moderate)
    PasswordScore.Strong -> stringResource(CoreItemR.string.password_strength_strong)
    PasswordScore.Excellent -> stringResource(CoreItemR.string.password_strength_excellent)
}

@Composable
private fun CredentialType.label(): String = when (this) {
    CredentialType.Password -> stringResource(CoreItemR.string.password)
    CredentialType.Passkey -> stringResource(R.string.credential_passkey)
    CredentialType.Totp -> stringResource(R.string.credential_totp)
}

private fun CredentialType.icon(): ImageVector = when (this) {
    CredentialType.Password -> Icons.Default.Password
    CredentialType.Passkey -> Icons.Default.Key
    CredentialType.Totp -> Icons.Default.Timer
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
                        onlyPinned = FilterChipState(value = true, selected = true),
                        itemTypeChips = VaultItemType.entries.map { type ->
                            FilterChipState(value = type, selected = false)
                        },
                        tagChips = listOf(
                            FilterChipState(value = Tag.of("Label1")!!, selected = false),
                            FilterChipState(value = Tag.of("Label2")!!, selected = true),
                        ),
                    ),
                    loginSection = LoginSectionState(
                        passwordScoreChips = listOf(
                            FilterChipState(value = PasswordScore.Excellent, selected = false),
                            FilterChipState(value = PasswordScore.Strong, selected = false),
                            FilterChipState(value = PasswordScore.Moderate, selected = true),
                            FilterChipState(value = PasswordScore.Weak, selected = true),
                            FilterChipState(value = PasswordScore.Ridiculous, selected = false),
                        ),
                        credentialChips = CredentialType.entries.map { type ->
                            FilterChipState(value = type, selected = type == CredentialType.Passkey)
                        },
                    ),
                    isDefault = false,
                ),
                onAction = {},
            )
        }
    }
}
