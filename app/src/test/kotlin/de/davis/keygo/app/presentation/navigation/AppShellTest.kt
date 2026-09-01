package de.davis.keygo.app.presentation.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.presentation.model.RouteDestination
import de.davis.keygo.feature.backup.presentation.BackupHubRoute
import de.davis.keygo.feature.settings.presentation.SettingsRoute
import kotlin.test.Test
import kotlin.test.assertEquals

class AppShellTest {

    private fun entry(key: NavKey, metadata: Map<String, Any>): NavEntry<NavKey> =
        NavEntry(key = key, metadata = metadata, content = {})

    private val home = entry(RouteDestination.Home, appShell(ShellVisibility.Always))

    private val viewItem = entry(
        RouteDestination.ViewItem(newItemId()),
        appShell(ShellVisibility.BesideListPane),
    )

    private val createItem = entry(
        RouteDestination.CreateItem(VaultItemType.Login),
        appShell(ShellVisibility.BesideListPane),
    )

    private val settings = entry(SettingsRoute, NavigationOnly)

    private val backup = entry(BackupHubRoute, WindowOwning)

    @Test
    fun `the list keeps the shell at any width`() {
        assertEquals(
            ResolvedAppShell(showNavigation = true, showCreateButton = true),
            listOf(home).resolveAppShell(listPaneVisible = true),
        )
        assertEquals(
            ResolvedAppShell(showNavigation = true, showCreateButton = true),
            listOf(home).resolveAppShell(listPaneVisible = false),
        )
    }

    @Test
    fun `an opened item hides the shell only once it has the window to itself`() {
        assertEquals(
            ResolvedAppShell(showNavigation = true, showCreateButton = true),
            listOf(home, viewItem).resolveAppShell(listPaneVisible = true),
        )
        assertEquals(
            ResolvedAppShell(showNavigation = false, showCreateButton = false),
            listOf(home, viewItem).resolveAppShell(listPaneVisible = false),
        )
    }

    @Test
    fun `a form follows the same rule as the item it was opened from`() {
        assertEquals(
            ResolvedAppShell(showNavigation = true, showCreateButton = true),
            listOf(home, viewItem, createItem).resolveAppShell(listPaneVisible = true),
        )
        assertEquals(
            ResolvedAppShell(showNavigation = false, showCreateButton = false),
            listOf(home, viewItem, createItem).resolveAppShell(listPaneVisible = false),
        )
    }

    @Test
    fun `settings keeps the navigation but not the create button`() {
        assertEquals(
            ResolvedAppShell(showNavigation = true, showCreateButton = false),
            listOf(home, settings).resolveAppShell(listPaneVisible = false),
        )
    }

    @Test
    fun `the backup flow takes the window at any width`() {
        assertEquals(
            ResolvedAppShell(showNavigation = false, showCreateButton = false),
            listOf(home, settings, backup).resolveAppShell(listPaneVisible = true),
        )
        assertEquals(
            ResolvedAppShell(showNavigation = false, showCreateButton = false),
            listOf(home, settings, backup).resolveAppShell(listPaneVisible = false),
        )
    }

    @Test
    fun `a destination that declares nothing shows up bare`() {
        val undeclared = entry(RouteDestination.Libraries, emptyMap())

        assertEquals(
            ResolvedAppShell(showNavigation = false, showCreateButton = false),
            listOf(home, undeclared).resolveAppShell(listPaneVisible = true),
        )
    }

    @Test
    fun `an empty back stack asks for nothing`() {
        assertEquals(
            ResolvedAppShell(showNavigation = false, showCreateButton = false),
            emptyList<NavEntry<NavKey>>().resolveAppShell(listPaneVisible = true),
        )
    }
}
