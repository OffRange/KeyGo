package de.davis.keygo.feature.autofill.presentation.activity

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.core.ui.navigation.KeyGoNavDisplay
import de.davis.keygo.feature.auth.presentation.authEntries
import de.davis.keygo.feature.autofill.presentation.model.SaveItemDestination
import de.davis.keygo.feature.item.create.presentation.EditVaultItemScreen
import de.davis.keygo.feature.list_screen.presentation.NoItemStrategy
import de.davis.keygo.feature.list_screen.presentation.itemListEntries

@Composable
internal fun AutofillUi(
    backStack: NavBackStack<NavKey>,
    onItemSelected: (ItemId) -> Unit,
    onSaved: () -> Unit,
    abort: () -> Unit,
    onAuthenticationSucceeded: () -> Unit,
) {
    KeyGoNavDisplay(
        backStack = backStack,
        entryProvider = entryProvider {
            authEntries(onSuccess = { onAuthenticationSucceeded() })

            itemListEntries(
                onItemClick = onItemSelected,
                restrictedItemType = VaultItemType.Login,
                dockedSearchResults = false,
                enableDeletion = false,
                onCreateRequest = {},
                notFoundStrategy = NoItemStrategy.ShowMessage
            )

            entry<SaveItemDestination> { destination ->
                EditVaultItemScreen(
                    detailPaneInformation = destination.createRaw,
                    onCreated = { onSaved() },
                    navigateBack = { abort() }
                )
            }
        }
    )
}
