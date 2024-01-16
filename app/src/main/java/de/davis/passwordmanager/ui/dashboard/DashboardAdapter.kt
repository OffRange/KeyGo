package de.davis.passwordmanager.ui.dashboard

import android.os.Bundle
import android.view.ViewGroup
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.SelectionTracker.SelectionPredicate
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import de.davis.passwordmanager.database.dtos.Item
import de.davis.passwordmanager.database.dtos.TagWithCount
import de.davis.passwordmanager.database.entities.shouldBeProtected
import de.davis.passwordmanager.ui.dashboard.managers.AbsItemManager
import de.davis.passwordmanager.ui.dashboard.selection.KeyProvider
import de.davis.passwordmanager.ui.dashboard.selection.SecureElementDetailsLookup
import de.davis.passwordmanager.ui.dashboard.viewholders.BasicViewHolder

class DashboardAdapter(private val onUpdate: (DashboardAdapter) -> Unit) :
    RecyclerView.Adapter<BasicViewHolder<Item>>() {

    private var itemManager: AbsItemManager<Item> = AbsItemManager.Empty()

    private lateinit var recyclerView: RecyclerView

    var filter: String = ""

    private lateinit var tracker: SelectionTracker<Long>

    init {
        setHasStableIds(true)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BasicViewHolder<Item> {
        return itemManager.createViewHolder(parent, viewType)
    }

    override fun getItemCount(): Int = itemManager.items.size

    override fun onBindViewHolder(holder: BasicViewHolder<Item>, position: Int) {
        itemManager.bind(holder, filter, position, tracker.isSelected(getItemId(position)))
    }

    override fun getItemViewType(position: Int): Int {
        return itemManager.getViewType(position)
    }

    override fun getItemId(position: Int): Long {
        return itemManager.getItemId(position)
    }

    fun applyRecyclerView(
        recyclerView: RecyclerView,
        enableSelection: Boolean = true,
        onSelectionChanged: (selectedElements: List<Item>) -> Unit = {}
    ) = recyclerView.apply {
        setHasFixedSize(true)
        this@DashboardAdapter.recyclerView = recyclerView
        adapter = this@DashboardAdapter

        tracker = SelectionTracker.Builder(
            "tracker",
            this,
            KeyProvider(this),
            SecureElementDetailsLookup(
                this
            ),
            StorageStrategy.createLongStorage()
        ).withSelectionPredicate(object : SelectionPredicate<Long>() {
            override fun canSetStateForKey(key: Long, nextState: Boolean): Boolean {
                val item = itemManager.getElementById(key)
                return enableSelection && !(item is TagWithCount && item.tag.shouldBeProtected)
            }

            override fun canSetStateAtPosition(position: Int, nextState: Boolean): Boolean {
                if (position !in itemManager.items.indices) return false

                val item = itemManager.items[position]
                return enableSelection && !(item is TagWithCount && item.tag.shouldBeProtected)
            }

            override fun canSelectMultiple(): Boolean {
                return enableSelection
            }

        }).build()

        tracker.addObserver(object : SelectionTracker.SelectionObserver<Long>() {
            var oldSelection: List<Long>? = null

            override fun onSelectionChanged() {
                if (tracker.selection.toList() == oldSelection)
                    return

                onSelectionChanged(getSelectedElements())
                oldSelection = tracker.selection.toList()
            }
        })
    }

    fun getSelectedElements(): List<Item> {
        return tracker.selection
            .map { itemManager.getElementById(it) }
            .mapNotNull { it }.toList()
    }

    fun clearSelection() = tracker.clearSelection()

    private fun configureRecyclerView() = recyclerView.apply {
        layoutManager = itemManager.getLayoutManager(recyclerView.context)
        for (i in 0 until recyclerView.itemDecorationCount) {
            recyclerView.removeItemDecorationAt(i)
        }
        itemManager.getItemDecoration()?.let {
            recyclerView.addItemDecoration(it)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <E : Item> update(itemManager: AbsItemManager<E>) {
        val old = this.itemManager.items

        this.itemManager = itemManager as AbsItemManager<Item>
        itemManager.prepareDataset()

        configureRecyclerView()

        if (old.toTypedArray().contentEquals(itemManager.items.toTypedArray()))
            return

        onUpdate(this)

        val callback = SecureElementDiffCallback(old, itemManager.items)
        val result = DiffUtil.calculateDiff(callback)
        result.dispatchUpdatesTo(this)
    }

    fun onSaveInstanceState(bundle: Bundle) {
        tracker.onSaveInstanceState(bundle)
    }

    fun onRestoreInstanceState(bundle: Bundle?) {
        tracker.onRestoreInstanceState(bundle)
    }
}