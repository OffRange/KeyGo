package de.davis.keygo.feature.item.create.presentation.creditcard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import org.koin.androidx.compose.koinViewModel

@Composable
internal fun CreditCardScreen(
    detailPaneInformation: DetailPaneInformation = DetailPaneInformation.Init.New(
        itemType = VaultItemType.CreditCard,
    ),
    creditCardCreated: (ItemId) -> Unit,
    navigateBack: () -> Unit,
) {
    val viewmodel: CreditCardViewModel = koinViewModel()
    val state by viewmodel.state.collectAsStateWithLifecycle()

    CreditCardContent(
        state = state,
        onEvent = viewmodel::onEvent
    )
}