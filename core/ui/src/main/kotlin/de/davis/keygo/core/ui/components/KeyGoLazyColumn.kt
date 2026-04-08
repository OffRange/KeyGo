package de.davis.keygo.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
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

sealed interface HeaderContent {
    data class Letter(val char: Char) : HeaderContent
    data object Pin : HeaderContent
}

data class KeyGoColumnItem<ID : Any>(
    val header: HeaderContent,
    val title: String,
    val id: ID,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    val (firstInGroupIndices, lastInGroupIndices) = remember(items) {
        val first = mutableSetOf<Int>()
        val last = mutableSetOf<Int>()

        for (i in items.indices) {
            if (i == 0 || items[i].header != items[i - 1].header) first.add(i)
            if (i == items.lastIndex || items[i].header != items[i + 1].header) last.add(i)
        }

        first to last
    }

    val listState = rememberLazyListState()
    val shapeCoordinator = rememberSegmentedShapeCoordinator()

    @Composable
    fun containerColorForId(id: ID): Color =
        if (id == openedItemId) OpenedContainerColor else ContainerColor

    Box(modifier = modifier.clipToBounds()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(ItemVerticalPadding)
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val id = item.id

                val isFirst = index in firstInGroupIndices
                val isLast = index in lastInGroupIndices

                val bottomPadding = if (isLast && index != items.lastIndex) GroupVerticalPadding
                else 0.dp

                val containerColor by animateColorAsState(containerColorForId(id))

                DeletableVaultItem(
                    title = item.title,
                    description = "TODO: description",
                    onClick = { onItemClick(id) },
                    onLongClick = { onItemLongClick(id) },
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
                    shapeCoordinator = shapeCoordinator,
                    itemIndex = index,
                    isFirst = isFirst,
                    isLast = isLast,
                    modifier = Modifier
                        .padding(bottom = bottomPadding)
                        .expressiveAnimateItem(),
                    selected = id in selectedItemIds,
                    containerColor = containerColor,
                    enableSwipeToDelete = enableSwipeToDelete,
                    leadingContent = {
                        val isFirstVisibleItem by remember(index) {
                            derivedStateOf { listState.firstVisibleItemIndex == index }
                        }

                        if (index in firstInGroupIndices && !isFirstVisibleItem) {
                            KeyGoInlineHeader(
                                header = item.header,
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

        val stickyHeader by remember(items) {
            derivedStateOf {
                if (listState.firstVisibleItemIndex < items.size)
                    items[listState.firstVisibleItemIndex].header
                else HeaderContent.Letter(' ')
            }
        }

        val density = LocalDensity.current
        val headerOffset by remember(listState, lastInGroupIndices, density) {
            derivedStateOf {
                val offset = with(density) {
                    IntOffset(16.dp.roundToPx(), 16.dp.roundToPx())
                }

                if (listState.firstVisibleItemIndex in lastInGroupIndices)
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
            header = stickyHeader,
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
context(scope: LazyItemScope)
private fun Modifier.expressiveAnimateItem(): Modifier = with(scope) {
    animateItem(
        fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        fadeOutSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    )
}

@Composable
private fun KeyGoInlineHeader(
    header: HeaderContent,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.headerSize(), contentAlignment = Alignment.Center) {
        when (header) {
            is HeaderContent.Letter -> Text(
                text = header.char.toString(),
                fontWeight = FontWeight.SemiBold,
                color = color,
            )

            is HeaderContent.Pin -> Icon(
                imageVector = Icons.Default.PushPin,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Stable
private fun Modifier.headerSize() = this.size(40.dp)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val ItemVerticalPadding = ListItemDefaults.SegmentedGap

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val GroupVerticalPadding = ListItemDefaults.SegmentedGap

private val ContainerColor
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh
private val OpenedContainerColor
    @Composable get() = MaterialTheme.colorScheme.primaryContainer
