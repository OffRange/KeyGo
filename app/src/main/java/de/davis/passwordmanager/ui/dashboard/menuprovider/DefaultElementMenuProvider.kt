package de.davis.passwordmanager.ui.dashboard.menuprovider

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.fragment.app.FragmentManager
import de.davis.passwordmanager.R
import de.davis.passwordmanager.database.dtos.Item
import de.davis.passwordmanager.ui.dashboard.DashboardAdapter
import de.davis.passwordmanager.ui.views.FilterBottomSheet
import de.davis.passwordmanager.ui.views.OptionBottomSheet

class DefaultElementMenuProvider(
    val manager: FragmentManager,
    private val adapter: DashboardAdapter
) : MenuProvider {

    var filterVisible: Boolean = false
    var selectedElements: List<Item> = emptyList()

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        menuInflater.inflate(R.menu.view_menu, menu)
    }

    override fun onPrepareMenu(menu: Menu) {
        super.onPrepareMenu(menu)
        menu.apply {
            findItem(R.id.more).setVisible(selectedElements.isNotEmpty())
            findItem(R.id.filter).setVisible(filterVisible)
        }
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        if (menuItem.itemId == R.id.more) {
            val optionBottomSheet = OptionBottomSheet(selectedElements)
            optionBottomSheet.show(manager, "MoreDialog")
            adapter.clearSelection()
        } else if (menuItem.itemId == R.id.filter) {
            FilterBottomSheet().show(manager, "FilterDialog")
        }
        return true
    }

    fun updateMenu(menuHost: MenuHost, block: DefaultElementMenuProvider.() -> Unit) {
        apply(block)
        menuHost.invalidateMenu()
    }
}

