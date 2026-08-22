package de.davis.keygo.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.item.generated.presentation.presentation
import kotlin.math.roundToInt

sealed interface HeaderContent {
    data class Letter(val char: Char) : HeaderContent
    data object Pin : HeaderContent
}

data class KeyGoColumnItem<ID : Any>(
    val header: HeaderContent,
    val title: String,
    val id: ID,
    val itemType: VaultItemType,
)

/**
 * The item the sticky header currently belongs to: [index] plus its [top] within the column.
 */
private data class StickyAnchor(val index: Int, val top: Int)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <ID : Any> KeyGoColumn(
    items: List<KeyGoColumnItem<ID>>,
    onItemClick: (ID) -> Unit,
    onItemLongClick: (ID) -> Unit,
    modifier: Modifier = Modifier,
    openedItemId: ID? = null,
    selectedItemIds: Set<ID> = emptySet(),
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current

    // The app bar above this column collapses away while scrolling, so the column can reach into
    // the status bar. Everything sticky is anchored below the status bar rather than at the very
    // top of the column, which would draw it behind the status bar.
    val statusBarTop = WindowInsets.statusBars.getTop(density)
    var columnTopInWindow by remember { mutableIntStateOf(statusBarTop) }
    val stickyTop by remember(statusBarTop) {
        derivedStateOf { (statusBarTop - columnTopInWindow).coerceAtLeast(0) }
    }

    // The first item reaching below the anchor owns the sticky header. With no status bar to
    // avoid this is exactly the first visible item.
    val anchor by remember(listState, stickyTop) {
        derivedStateOf {
            val info = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                it.offset + it.size > stickyTop
            }

            StickyAnchor(
                index = info?.index ?: listState.firstVisibleItemIndex,
                top = info?.offset ?: 0,
            )
        }
    }
    val anchorIndex by remember(anchor) { derivedStateOf { anchor.index } }

    @Composable
    fun containerColorForId(id: ID): Color =
        if (id == openedItemId) OpenedContainerColor else ContainerColor

    Box(
        modifier = modifier
            .clipToBounds()
            .onGloballyPositioned { columnTopInWindow = it.positionInWindow().y.roundToInt() }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(ItemVerticalPadding)
        ) {
            itemsIndexed(
                items,
                key = { _, item -> item.id },
                contentType = { _, item -> item.itemType }
            ) { index, item ->
                val id = item.id

                val isFirst = items.isFirstInGroup(index)
                val isLast = items.isLastInGroup(index)

                val bottomPadding = if (isLast && index != items.lastIndex) GroupVerticalPadding
                else 0.dp

                val containerColor by animateColorAsState(containerColorForId(id))

                SegmentedListItem(
                    onClick = { onItemClick(id) },
                    onLongClick = { onItemLongClick(id) },
                    shapes = segmentedShapesFor(isFirst = isFirst, isLast = isLast),
                    colors = ListItemDefaults.segmentedColors(
                        containerColor = containerColor,
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                    modifier = Modifier
                        .padding(bottom = bottomPadding)
                        .expressiveAnimateItem(),
                    leadingContent = {
                        // The anchor row's letter is the sticky one. Below the anchor a group
                        // announces itself on its first row as usual; above it, a group that is
                        // on its way out rides its letter off the screen on its last row.
                        val showsHeader by remember(index, isFirst, isLast, anchorIndex) {
                            derivedStateOf {
                                val anchor = anchorIndex
                                when {
                                    index > anchor -> isFirst
                                    index < anchor -> isLast
                                    else -> false
                                }
                            }
                        }

                        if (showsHeader) {
                            KeyGoInlineHeader(
                                header = item.header,
                                color = contentColorFor(containerColorForId(id))
                            )
                        } else {
                            Spacer(modifier = Modifier.headerSize())
                        }
                    },
                    supportingContent = {
                        Text(text = item.itemType.presentation.first)
                    },
                    selected = id in selectedItemIds,
                ) {
                    Text(text = item.title)
                }
            }
        }

        // ----------- HEADER -----------
        if (items.isEmpty()) return

        val stickyHeader by remember(anchor, items) {
            derivedStateOf {
                items.getOrNull(anchor.index)?.header ?: HeaderContent.Letter(' ')
            }
        }

        val headerOffset by remember(anchor, stickyTop, items, density) {
            derivedStateOf {
                val offset = with(density) {
                    IntOffset(16.dp.roundToPx(), (10.dp + 6.dp).roundToPx())
                }

                val (index, top) = anchor
                val pinned = stickyTop

                // The last item of a group takes its header with it instead of holding it pinned.
                val headerTop = if (index in items.indices && items.isLastInGroup(index))
                    minOf(pinned, top)
                else pinned

                offset.copy(y = offset.y + headerTop)
            }
        }

        var headerPositionY by remember { mutableFloatStateOf(0f) }

        // headerPositionY is in column coordinates, so both sides are shifted back to the
        // anchor to tell whether the header has drifted over the next item.
        val idBehindHeader by remember(anchor, stickyTop, items, headerOffset) {
            derivedStateOf {
                val (index, top) = anchor
                val pinned = stickyTop
                val id = if (
                    pinned - top >= headerPositionY - pinned &&
                    headerOffset.y >= pinned &&
                    index + 1 in items.indices
                ) index + 1
                else index

                items[id.coerceIn(items.indices)].id
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

private fun List<KeyGoColumnItem<*>>.isFirstInGroup(index: Int) =
    index == 0 || this[index].header != this[index - 1].header

private fun List<KeyGoColumnItem<*>>.isLastInGroup(index: Int) =
    index == lastIndex || this[index].header != this[index + 1].header

@Composable
private fun segmentedShapesFor(isFirst: Boolean, isLast: Boolean): ListItemShapes = when {
    isFirst && isLast -> ListItemDefaults.shapes(MaterialTheme.shapes.large)
    isFirst -> ListItemDefaults.segmentedShapes(index = 0, count = 3)
    isLast -> ListItemDefaults.segmentedShapes(index = 2, count = 3)
    else -> ListItemDefaults.segmentedShapes(index = 1, count = 3)
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


@Preview
@Composable
private fun KeyGoLazyColumnPreview() {
    val items = remember {
        List(100) {
            KeyGoColumnItem(
                header = HeaderContent.Letter('A' + it / 10),
                title = "Item $it",
                id = it,
                itemType = VaultItemType.Login,
            )
        }.sortedBy { (it.header as HeaderContent.Letter).char }
    }

    MaterialTheme {
        Surface {
            KeyGoColumn(
                items = items,
                onItemClick = {},
                onItemLongClick = {},
            )
        }
    }
}