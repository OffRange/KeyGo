package de.davis.passwordmanager.ui.dashboard.managers

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import androidx.recyclerview.widget.RecyclerView.LayoutManager
import de.davis.passwordmanager.database.dtos.Item
import de.davis.passwordmanager.ui.dashboard.viewholders.BasicViewHolder

sealed class AbsItemManager<E : Item>(
    initialItems: List<E>,
    private val onClick: ((E) -> Unit)? = null
) {

    val items = mutableListOf<E>().apply { addAll(initialItems) }

    open fun prepareDataset() = items.apply {
        sortItems()
    }

    fun update(collection: Collection<E>) = items.apply {
        clear()
        addAll(collection)
    }

    abstract fun createViewHolder(parent: ViewGroup, viewType: Int): BasicViewHolder<E>
    abstract fun getLayoutManager(context: Context): LayoutManager
    open fun getItemDecoration(): ItemDecoration? = null
    abstract fun getItemId(position: Int): Long
    abstract fun MutableList<E>.sortItems()

    abstract fun getViewType(position: Int): Int

    open fun bind(
        viewHolder: BasicViewHolder<E>,
        filter: String,
        position: Int,
        selected: Boolean
    ) {
        viewHolder.bind(items[position], filter, onClick, selected)
    }

    fun getElementById(id: Long): E? {
        return items.mapIndexed { index, e -> getItemId(index) to e }
            .find { (eId, _) -> eId == id }?.second
    }

    class Empty : AbsItemManager<Item>(arrayListOf()) {

        override fun getViewType(position: Int): Int = 0

        override fun createViewHolder(parent: ViewGroup, viewType: Int): BasicViewHolder<Item> {
            throw IllegalArgumentException("Can not call createViewHolder on a dummy Manager")
        }

        override fun getLayoutManager(context: Context): LayoutManager {
            throw IllegalArgumentException("Can not call getLayoutManager on a dummy Manager")
        }

        override fun getItemId(position: Int): Long = 0

        override fun MutableList<Item>.sortItems() {}

    }
}

