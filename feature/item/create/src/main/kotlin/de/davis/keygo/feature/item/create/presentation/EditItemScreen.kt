package de.davis.keygo.feature.item.create.presentation

import androidx.compose.runtime.Composable
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.feature.item.create.presentation.login.LoginScreen

@Composable
fun EditVaultItemScreen(
    detailPaneInformation: DetailPaneInformation,
    onCreated: (ItemId) -> Unit,
    navigateBack: () -> Unit,
) {
    when (detailPaneInformation) {
        is DetailPaneInformation.Init -> ForInit(
            detailPaneInformation,
            onCreated,
            navigateBack,
        )

        is DetailPaneInformation.CreateRaw -> ForRawItem(
            detailPaneInformation,
            onCreated,
            navigateBack,
        )
    }
}

@Composable
private fun ForInit(
    info: DetailPaneInformation.Init,
    onCreated: (ItemId) -> Unit,
    navigateBack: () -> Unit,
) {
    when (info.itemType) {
        VaultItemType.Login -> LoginScreen(
            detailPaneInformation = info,
            loginCreated = onCreated,
            navigateBack = navigateBack,
        )
    }
}

@Composable
private fun ForRawItem(
    item: DetailPaneInformation.CreateRaw,
    onCreated: (ItemId) -> Unit,
    navigateBack: () -> Unit,
) {
    when (item) {
        is DetailPaneInformation.CreateRaw.Login -> LoginScreen(
            detailPaneInformation = item,
            loginCreated = onCreated,
            navigateBack = navigateBack,
        )
    }
}
