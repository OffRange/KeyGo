package de.davis.passwordmanager.dashboard

import androidx.recyclerview.widget.DiffUtil
import de.davis.passwordmanager.database.dtos.SecureElement

class SecureElementDiffCallback(
    private val oldItems: List<Item>,
    private val newItems: List<Item>
) : DiffUtil.Callback() {
    override fun getOldListSize(): Int {
        return oldItems.size
    }

    override fun getNewListSize(): Int {
        return newItems.size
    }

    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        return oldItems[oldItemPosition].id == newItems[newItemPosition].id
    }

    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
        val oldItem = oldItems[oldItemPosition]
        val newItem = newItems[newItemPosition]
        return if (oldItem is SecureElement && newItem is SecureElement) {
            oldItem == newItem
        } else false
    }
}