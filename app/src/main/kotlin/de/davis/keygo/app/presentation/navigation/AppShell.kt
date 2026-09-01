package de.davis.keygo.app.presentation.navigation

import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.get
import androidx.navigation3.runtime.metadata

enum class ShellVisibility {
    Always,
    Never,
    BesideListPane,
}

/**
 * The components drawn around a destination: the navigation bar, rail or drawer, and the create
 * button that starts a new item.
 */
data class AppShell(
    val navigation: ShellVisibility,
    val createButton: ShellVisibility,
)

data class ResolvedAppShell(
    val showNavigation: Boolean,
    val showCreateButton: Boolean,
)

/**
 * Metadata declaring the shell a destination wants.
 *
 * @param createButton defaults to following [navigation]
 */
fun appShell(
    navigation: ShellVisibility,
    createButton: ShellVisibility = navigation,
): Map<String, Any> = metadata { put(AppShellKey, AppShell(navigation, createButton)) }

/**
 * The shell the topmost entry asks for.
 *
 * @param listPaneVisible whether the window is wide enough to show the list beside a detail, taken
 *   from the same scaffold directive the list-detail scene lays itself out with
 */
fun List<NavEntry<NavKey>>.resolveAppShell(listPaneVisible: Boolean): ResolvedAppShell {
    val requested = lastOrNull()?.metadata?.get(AppShellKey) ?: WindowOwningShell
    return ResolvedAppShell(
        showNavigation = requested.navigation.isVisible(listPaneVisible),
        showCreateButton = requested.createButton.isVisible(listPaneVisible),
    )
}

private val WindowOwningShell = AppShell(ShellVisibility.Never, ShellVisibility.Never)

private object AppShellKey : NavMetadataKey<AppShell> {
    override fun toString(): String = "de.davis.keygo.app.shell"
}

private fun ShellVisibility.isVisible(listPaneVisible: Boolean): Boolean = when (this) {
    ShellVisibility.Always -> true
    ShellVisibility.Never -> false
    ShellVisibility.BesideListPane -> listPaneVisible
}

/**
 * The destination owns the whole window: no navigation, no create button. Also the fallback for a
 * destination that declares nothing, so a new screen shows up bare rather than borrowing chrome.
 */
val WindowOwning: Map<String, Any> = metadata { put(AppShellKey, WindowOwningShell) }

val NavigationOnly: Map<String, Any> = appShell(
    navigation = ShellVisibility.Always,
    createButton = ShellVisibility.Never,
)
