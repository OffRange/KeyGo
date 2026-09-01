package de.davis.keygo.feature.onboarding.presentation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

fun EntryProviderScope<NavKey>.onboardingEntries(
    metadata: Map<String, Any> = emptyMap(),
    onSuccess: (String?) -> Unit,
) {
    entry<OnboardingRoute>(metadata = metadata) { route ->
        OnboardingScreen(route = route, onSuccess = { onSuccess(route.uri) })
    }
}

/** The import travels whole: back stack keys are saved with kotlinx.serialization. */
@Serializable
data class OnboardingRoute(val uri: String? = null) : NavKey
