package de.davis.keygo.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp

data class KeyGoColumnItem<ID : Any>(
    val header: Char,
    val title: String,
    val id: ID,
)

@Composable
fun <ID : Any> KeyGoColumn(
    items: List<KeyGoColumnItem<ID>>,
    onDelete: (ID) -> Unit,
    onItemClick: (ID) -> Unit,
    onItemLongClick: (ID) -> Unit,
    modifier: Modifier = Modifier,
    enableSwipeToDelete: Boolean = true,
    openedItemId: ID? = null,
    selectedItemIds: Set<ID> = emptySet(),
) {
    val groupedItems = remember(items) {
        items.groupBy { it.header }
    }

    val (firstElementIndices, lastElementIndices) = remember(groupedItems) {
        val values = groupedItems.values
        val firstElementIndices = values.map { groupItems -> items.indexOf(groupItems.first()) }
        val lastElementIndices = values.map { groupItems -> items.indexOf(groupItems.last()) }
        firstElementIndices to lastElementIndices
    }

    val listState = rememberLazyListState()

    @Composable
    fun containerColorForId(id: ID): Color = when (id) {
        in selectedItemIds -> SelectedContainerColor
        openedItemId -> OpenedContainerColor
        else -> ContainerColor
    }

    Box(modifier = modifier.clipToBounds()) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(ItemVerticalPadding)
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val id = item.id
                DeletableVaultItem(
                    title = item.title,
                    description = "TODO: description",
                    onDeleteRequested = {
                        onDelete(id)
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
                    modifier = Modifier
                        .combinedClickable(
                            onClick = { onItemClick(id) },
                            onLongClick = { onItemLongClick(id) }
                        )
                        .animateItem(),
                    enableSwipeToDelete = enableSwipeToDelete,
                    cardColors = CardDefaults.cardColors(
                        containerColor = containerColorForId(id),
                    ),
                    leadingContent = {
                        val isFirstVisibleItem by remember {
                            derivedStateOf { listState.firstVisibleItemIndex == index }
                        }

                        if (index in firstElementIndices && !isFirstVisibleItem) {
                            KeyGoInlineHeader(
                                header = item.header.toString(),
                                color = contentColorFor(containerColorForId(id))
                            )
                        } else {
                            Spacer(modifier = Modifier.headerSize())
                        }
                    }
                )
            }
        }

        // ----------- HEADER -----------
        if (items.isEmpty()) return

        val headerContent by remember(items) {
            derivedStateOf {
                if (listState.firstVisibleItemIndex < items.size) {
                    items[listState.firstVisibleItemIndex].header.toString()
                } else ""
            }
        }

        val density = LocalDensity.current
        val headerOffset by remember(listState, lastElementIndices, density) {
            derivedStateOf {
                val offset = with(density) {
                    IntOffset(16.dp.roundToPx(), 16.dp.roundToPx())
                }

                if (listState.firstVisibleItemIndex in lastElementIndices)
                    offset.copy(y = offset.y - listState.firstVisibleItemScrollOffset)
                else offset
            }
        }

        var headerPositionY by remember { mutableFloatStateOf(0f) }

        val idBehindHeader by remember(listState, items) {
            derivedStateOf {
                val visibleItemIdx = listState.firstVisibleItemIndex
                val id = if (
                    listState.firstVisibleItemScrollOffset >= headerPositionY &&
                    headerOffset.y >= 0 &&
                    visibleItemIdx + 1 in items.indices
                ) visibleItemIdx + 1
                else visibleItemIdx

                items[id].id
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
                        it.positionInParent().y + it.size.height / 2f + with(density) {
                            ItemVerticalPadding.roundToPx() / 2f
                        }
                }
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

val ItemVerticalPadding = 8.dp

private val ContainerColor
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
private val OpenedContainerColor
    @Composable get() = MaterialTheme.colorScheme.primaryContainer
private val SelectedContainerColor
    @Composable get() = MaterialTheme.colorScheme.secondaryContainer