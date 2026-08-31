package de.davis.keygo.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator

/**
 * The decorators every back stack is rendered with: each destination keeps its own saved state and
 * view models while it is on the stack. Every stack gets its own call, so none of it is shared.
 */
@Composable
fun rememberNavEntryDecorators(): List<NavEntryDecorator<NavKey>> {
    val saveableState = rememberSaveableStateHolderNavEntryDecorator<NavKey>()
    val viewModelStore = rememberViewModelStoreNavEntryDecorator<NavKey>()

    return remember(saveableState, viewModelStore) { listOf(saveableState, viewModelStore) }
}
