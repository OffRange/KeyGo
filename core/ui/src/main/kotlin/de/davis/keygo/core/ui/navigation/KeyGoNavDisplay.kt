package de.davis.keygo.core.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.ui.NavDisplay
import androidx.navigation3.ui.defaultPopTransitionSpec

/**
 * Renders [backStack] across the whole window of its host.
 *
 * This is the shape the satellite activities want: one throwaway stack, no navigation bar and no
 * detail pane. Back pops, and popping the last entry is what closes the host, so a flow the system
 * started can always be backed out of.
 *
 * The app itself owns several stacks and draws its own shell around them, so it uses the [NavEntry]
 * overload instead.
 */
@Composable
fun KeyGoNavDisplay(
    backStack: NavBackStack<NavKey>,
    modifier: Modifier = Modifier,
    entryProvider: (NavKey) -> NavEntry<NavKey>,
) {
    Scaffold(modifier = modifier) { innerPadding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            entryDecorators = rememberNavEntryDecorators(),
            predictivePopTransitionSpec = KeyGoPredictivePopTransitionSpec,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
            entryProvider = entryProvider,
        )
    }
}

/**
 * Renders entries that are already decorated, inside a shell the caller has drawn.
 *
 * Decorating happens outside so a caller holding more than one stack can keep each one decorated on
 * its own, letting a route that is off screen hold on to its saved state and view models. Nothing
 * here draws a background or handles insets: whatever the entries are placed in owns that.
 */
@Composable
fun KeyGoNavDisplay(
    entries: List<NavEntry<NavKey>>,
    onBack: () -> Unit,
    sceneStrategies: List<SceneStrategy<NavKey>>,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        entries = entries,
        onBack = onBack,
        sceneStrategies = sceneStrategies,
        predictivePopTransitionSpec = KeyGoPredictivePopTransitionSpec,
        modifier = modifier,
    )
}

private val KeyGoPredictivePopTransitionSpec:
        AnimatedContentTransitionScope<Scene<NavKey>>.(Int) -> ContentTransform =
    { defaultPopTransitionSpec<NavKey>()(this) }
