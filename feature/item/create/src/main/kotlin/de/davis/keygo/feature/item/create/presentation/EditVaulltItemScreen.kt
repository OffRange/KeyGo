package de.davis.keygo.feature.item.create.presentation

import androidx.compose.runtime.Composable
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.feature.item.core.presentation.model.NavigationEvent
import de.davis.keygo.feature.item.create.presentation.password.PasswordScreen

@Composable
fun EditVaultItemScreen(
    detailPaneInformation: DetailPaneInformation,
    navigate: (NavigationEvent) -> Unit
) {
    when (detailPaneInformation) {
        is DetailPaneInformation.Init -> ForInit(
            detailPaneInformation,
            navigate
        )

        is DetailPaneInformation.CreateRaw -> ForRawItem(
            detailPaneInformation,
            navigate
        )
    }
}

@Composable
private fun ForInit(
    info: DetailPaneInformation.Init,
    navigate: (NavigationEvent) -> Unit
) {
    when (info.itemType) {
        VaultItemType.Password -> PasswordScreen(
            detailPaneInformation = info,
            navigate = navigate
        )
    }
}

@Composable
private fun ForRawItem(item: DetailPaneInformation.CreateRaw, navigate: (NavigationEvent) -> Unit) {
    when (item) {
        is DetailPaneInformation.CreateRaw.Password -> PasswordScreen(
            detailPaneInformation = item,
            navigate = navigate
        )
    }
}