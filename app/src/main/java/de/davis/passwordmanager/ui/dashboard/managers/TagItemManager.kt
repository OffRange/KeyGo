package de.davis.passwordmanager.ui.dashboard.managers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import de.davis.passwordmanager.R
import de.davis.passwordmanager.database.TAG_PREFIX
import de.davis.passwordmanager.database.dtos.TagWithCount
import de.davis.passwordmanager.database.entities.Tag
import de.davis.passwordmanager.database.entities.getLocalizedName
import de.davis.passwordmanager.database.entities.shouldBeProtected
import de.davis.passwordmanager.ui.GridLayoutManager
import de.davis.passwordmanager.ui.dashboard.viewholders.BasicViewHolder
import de.davis.passwordmanager.ui.views.InformationView
import net.greypanther.natsort.SimpleNaturalComparator

class TagItemManager(
    initialItems: List<TagWithCount>,
    onClick: ((TagWithCount) -> Unit)?
) :
    AbsItemManager<TagWithCount>(initialItems, onClick) {

    override fun createViewHolder(parent: ViewGroup, viewType: Int): BasicViewHolder<TagWithCount> {
        return if (viewType == VIEW_TYPE_DUMMY) {
            return object : BasicViewHolder<TagWithCount>(
                FrameLayout(parent.context)
            ) {
                override fun handleSelectionState(selected: Boolean) {}

                override fun bindGeneral(
                    item: TagWithCount,
                    filter: String?,
                    onItemClickedListener: OnItemClickedListener<TagWithCount>?
                ) {
                }
            }
        } else {
            object : BasicViewHolder<TagWithCount>(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.layout_tag_item, parent, false)
            ) {
                override fun handleSelectionState(selected: Boolean) {
                    (itemView as MaterialCardView).isChecked = selected
                }

                override fun bindGeneral(
                    item: TagWithCount,
                    filter: String?,
                    onItemClickedListener: OnItemClickedListener<TagWithCount>?
                ) {
                    (itemView as InformationView).apply {
                        setTitle(item.tag.getLocalizedName(parent.context))
                        setInformationText(context.getString(R.string.items_n, item.count))
                    }

                    if (item.count > 0)
                        itemView.setOnClickListener {
                            onItemClickedListener?.onClicked(item)
                        }
                }
            }
        }
    }

    override fun getLayoutManager(context: Context): RecyclerView.LayoutManager {
        return GridLayoutManager(context)
    }

    override fun getItemDecoration(): RecyclerView.ItemDecoration {
        return object : RecyclerView.ItemDecoration() {

            private val spanCount = 2
            private val dividerHeightDip = 1.5f

            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                val position = parent.getChildAdapterPosition(view)
                val column = position % spanCount
                val spacing = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    8f,
                    parent.context.resources.displayMetrics
                ).toInt()

                outRect.left = spacing - column * spacing / spanCount
                outRect.right = (column + 1) * spacing / spanCount

                if (position < spanCount) {
                    outRect.top = spacing
                }
                outRect.bottom =
                    if (position <= getLastDefaultTagPosition()) {
                        spacing * 2 + TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            dividerHeightDip,
                            parent.context.resources.displayMetrics
                        ).toInt()
                    } else
                        spacing
            }

            private val paint = Paint().apply {
                style = Paint.Style.FILL
            }

            override fun onDrawOver(c: Canvas, parent: RecyclerView, state: RecyclerView.State) {
                val child = parent.getChildAt(getLastDefaultTagPosition())
                val spacing = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    8f,
                    parent.context.resources.displayMetrics
                ).toInt()

                // Calculate the position for the divider
                val params = child.layoutParams as RecyclerView.LayoutParams
                val top = child.bottom + params.bottomMargin + spacing
                val bottom = top + TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    dividerHeightDip,
                    parent.context.resources.displayMetrics
                ).toInt()

                // Draw the divider
                c.drawRect(
                    spacing.toFloat(),
                    top.toFloat(),
                    parent.right - spacing.toFloat(),
                    bottom.toFloat(),
                    paint.apply {
                        color = MaterialColors.getColor(
                            parent,
                            com.google.android.material.R.attr.colorOutlineVariant
                        )
                    }
                )
            }
        }
    }

    override fun getItemId(position: Int): Long {
        return if (items[position].id == Long.MIN_VALUE) Long.MIN_VALUE else -items[position].tag.tagId - 5
    }

    override fun getViewType(position: Int): Int {
        return if (items[position].id == Long.MIN_VALUE) VIEW_TYPE_DUMMY else VIEW_TYPE_TAG
    }

    override fun MutableList<TagWithCount>.sortItems() {
        sortWith(
            compareByDescending<TagWithCount> { it.tag.shouldBeProtected }.then(
                compareBy(
                    SimpleNaturalComparator.getInstance()
                ) { it.tag.name })
        )
    }

    override fun prepareDataset(): MutableList<TagWithCount> {
        return super.prepareDataset().apply {
            val lastDefaultTagPosition = getLastDefaultTagPosition()
            if ((lastDefaultTagPosition + 1) % 2 /* span count = 2 */ != 0)
                add(
                    lastDefaultTagPosition + 1,
                    TagWithCount(Tag("$TAG_PREFIX:DUMMY", Long.MIN_VALUE), -1)
                )
        }
    }

    fun getLastDefaultTagPosition(): Int {
        return items.indexOfLast { it.tag.shouldBeProtected }
    }

    companion object {
        private const val VIEW_TYPE_TAG = 2
        private const val VIEW_TYPE_DUMMY = 3
    }
}