package de.davis.passwordmanager.ui.dashboard.managers

import android.content.Context
import android.graphics.Rect
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import de.davis.passwordmanager.R
import de.davis.passwordmanager.database.dtos.TagWithCount
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

    override fun createViewHolder(parent: ViewGroup): BasicViewHolder<TagWithCount> {
        return object : BasicViewHolder<TagWithCount>(
            LayoutInflater.from(parent.context)
                .inflate(R.layout.layout_tag_item, parent, false)
        ) {
            override fun getItemDetails(): ItemDetailsLookup.ItemDetails<Long> {
                return object : ItemDetailsLookup.ItemDetails<Long>() {
                    override fun getPosition(): Int = absoluteAdapterPosition
                    override fun getSelectionKey(): Long = itemId
                }
            }

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

    override fun getLayoutManager(context: Context): RecyclerView.LayoutManager {
        return GridLayoutManager(context)
    }

    override fun getItemDecoration(): RecyclerView.ItemDecoration {
        return object : RecyclerView.ItemDecoration() {

            private val spanCount = 2

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
                outRect.bottom = spacing
            }
        }
    }

    override fun getItemId(position: Int): Long {
        return -items[position].tag.tagId - 5
    }

    override val viewType: Int
        get() = 2

    override fun MutableList<TagWithCount>.sortItems() {
        sortWith(
            compareByDescending<TagWithCount> { it.tag.shouldBeProtected }.then(
                compareBy(
                    SimpleNaturalComparator.getInstance()
                ) { it.tag.name })
        )
    }
}