package de.davis.keygo.feature.auth.presentation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey

fun EntryProviderScope<NavKey>.authEntries(
    metadata: Map<String, Any> = emptyMap(),
    onSuccess: (String?) -> Unit,
) {
    entry<AuthRoute>(metadata = metadata) { route ->
        AuthScreen(route = route, onSuccess = { onSuccess(route.uri) })
    }
}
