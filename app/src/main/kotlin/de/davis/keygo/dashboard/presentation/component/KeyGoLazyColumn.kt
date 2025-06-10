package de.davis.keygo.dashboard.presentation.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.core.domain.model.crypto.CryptographicData
import de.davis.keygo.core.domain.`typealias`.ItemId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList

private fun headerProducer(item: VaultItem) = item.name.firstOrNull() ?: ' '

@Composable
fun KeyGoLazyColumn(
    items: ImmutableList<VaultItem>,
    selectedItemIds: ImmutableSet<ItemId>,
    openedItemId: ItemId,
    onDeleteRequest: (ItemId) -> Unit,
    onItemClick: (ItemId) -> Unit,
    onItemLongClick: (ItemId) -> Unit,
    modifier: Modifier = Modifier
) {
    val groupedItems = remember(items) {
        items.groupBy(::headerProducer)
    }

    val firstElementIndexes = remember(groupedItems) {
        groupedItems.mapValues { (_, groupItems) -> items.indexOf(groupItems.first()) }.values
    }

    val lastElementIndexes = remember(groupedItems) {
        groupedItems.mapValues { (_, groupItems) -> items.indexOf(groupItems.last()) }.values
    }

    val listState = rememberLazyListState()

    val containerColorForId =
        remember<@Composable (ItemId) -> Color>(openedItemId, selectedItemIds) {
            @Composable { id: ItemId ->
                when (id) {
                    in selectedItemIds -> SelectedContainerColor
                    openedItemId -> OpenedContainerColor
                    else -> ContainerColor
                }
            }
        }

    Box(modifier = modifier.clipToBounds()) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(ItemVerticalPadding)
        ) {
            itemsIndexed(items, key = { _, item -> item.vaultItemId }) { index, item ->
                KeyGoLazyItem(
                    item = item,
                    header = {
                        val isFirstVisibleItem by remember {
                            derivedStateOf { listState.firstVisibleItemIndex == index }
                        }

                        if (index in firstElementIndexes && !isFirstVisibleItem) {
                            KeyGoInlineHeader(
                                header = headerProducer(item).toString(),
                                color = contentColorFor(containerColorForId(item.vaultItemId))
                            )
                        } else {
                            Spacer(modifier = Modifier.headerSize())
                        }
                    },
                    onDeleteRequest = { onDeleteRequest(item.vaultItemId) },
                    modifier = Modifier.combinedClickable(
                        onClick = { onItemClick(item.vaultItemId) },
                        onLongClick = { onItemLongClick(item.vaultItemId) }
                    ),
                    containerColor = containerColorForId(item.vaultItemId)
                )
            }
        }

        if (items.isEmpty()) {
            return
        }
        val headerContent by remember(items) {
            derivedStateOf {
                if (listState.firstVisibleItemIndex < items.size)
                    headerProducer(items[listState.firstVisibleItemIndex]).toString()
                else
                    ""
            }
        }

        val density = LocalDensity.current
        val offset = with(density) {
            IntOffset(16.dp.roundToPx(), 16.dp.roundToPx())
        }

        val headerOffset by remember(lastElementIndexes) {
            derivedStateOf {
                if (listState.firstVisibleItemIndex in lastElementIndexes)
                    offset.copy(y = offset.y - listState.firstVisibleItemScrollOffset)
                else
                    offset
            }
        }

        var headerPositionY by remember { mutableFloatStateOf(0f) }

        val idBehindHeader by remember(items) {
            derivedStateOf {
                val visibleItemIndex = listState.firstVisibleItemIndex
                items[
                    if (
                        listState.firstVisibleItemScrollOffset >= headerPositionY &&
                        headerOffset.y >= 0 &&
                        visibleItemIndex + 1 in items.indices
                    ) visibleItemIndex + 1
                    else visibleItemIndex
                ].vaultItemId
            }
        }

        val color by animateColorAsState(
            contentColorFor(containerColorForId(idBehindHeader))
        )

        KeyGoInlineHeader(
            header = headerContent,
            color = color,
            modifier = Modifier
                .offset { headerOffset }
                .onGloballyPositioned {
                    headerPositionY =
                        it.positionInParent().y + (it.size.height) / 2 + with(density) {
                            ItemVerticalPadding.roundToPx() / 2
                        }
                }
        )
    }
}

@Composable
private fun LazyItemScope.KeyGoLazyItem(
    item: VaultItem,
    header: @Composable () -> Unit,
    onDeleteRequest: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color.Unspecified,
    contentColor: Color = contentColorFor(containerColor),
) {
    DeletableVaultItem(
        title = item.name,
        description = "TODO type of Item",
        onDeleteRequested = {
            onDeleteRequest()
            // We return false here because it allows the swipe state to reset.
            // When an item is successfully deleted, a new list containing the
            // remaining items will be provided, causing the list to recompose
            // with the updated data. The swipe state doesn't need to persist beyond
            // the delete action, so resetting it is appropriate.
            // If an error occurs during deletion and the item isn't removed, the
            // swipe state will reset automatically since no new list is provided
            // and no recomposition occurs. This ensures the UI remains consistent.
            false
        },
        modifier = modifier.animateItem(),
        cardColors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        leadingContent = header
    )
}

@Preview(showBackground = true)
@Composable
private fun KeyGoLazyColumnPreview() {
    MaterialTheme {
        KeyGoLazyColumn(
            items = buildList {
                repeat(25) {
                    val p = Password(
                        it.toLong(),
                        "Item $it",
                        "Description $it",
                        it.toLong(),
                        "${if (it >= 5) 'a' else 'P'}assword $it",
                        CryptographicData.EMPTY,
                        "Password $it"
                    )
                    add(p)
                }
            }.toPersistentList(),
            selectedItemIds = persistentSetOf(4),
            openedItemId = -1,
            onDeleteRequest = {},
            onItemClick = {},
            onItemLongClick = {},
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun KeyGoInlineHeader(header: String, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.headerSize(), contentAlignment = Alignment.Center) {
        Text(
            text = header,
            modifier = Modifier,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}


@Stable
private fun Modifier.headerSize() = this.size(40.dp)

internal val ItemVerticalPadding = 8.dp

private val ContainerColor
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
private val OpenedContainerColor
    @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val SelectedContainerColor
    @Composable get() = MaterialTheme.colorScheme.secondaryContainer