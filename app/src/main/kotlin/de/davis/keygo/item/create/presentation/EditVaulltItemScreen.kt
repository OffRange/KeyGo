package de.davis.keygo.item.create.presentation

import androidx.compose.runtime.Composable
import de.davis.keygo.core.domain.model.Password
import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.core.domain.model.VaultSearchResult
import de.davis.keygo.item.core.presentation.model.DetailPaneInformation
import de.davis.keygo.item.core.presentation.model.DetailPaneInformation.CreateRaw
import de.davis.keygo.item.core.presentation.model.DetailPaneInformation.InitByDetailType
import de.davis.keygo.core.presentation.model.NavigationEvent
import de.davis.keygo.generated.item.VaultItemType
import de.davis.keygo.item.core.presentation.model.DetailType
import de.davis.keygo.item.create.presentation.password.PasswordScreen

@Composable
fun EditVaultItemScreen(
    detailPaneInformation: DetailPaneInformation,
    navigate: (NavigationEvent) -> Unit
) {
    when (detailPaneInformation) {
        is InitByDetailType -> ForType(
            detailPaneInformation,
            navigate
        )

        is CreateRaw -> ForVaultItemInstance(
            detailPaneInformation,
            navigate
        )
    }
}

@Composable
private fun ForType(info: InitByDetailType, navigate: (NavigationEvent) -> Unit) {
    when (val type = info.detailType) {
        is DetailType.Modify -> when (type.vaultItemType) {
            VaultItemType.Password -> PasswordScreen(
                detailPaneInformation = info,
                navigate = navigate
            )
        }

        is DetailType.View -> throw IllegalArgumentException("View type is not supported")
    }
}

@Composable
private fun ForVaultItemInstance(item: CreateRaw, navigate: (NavigationEvent) -> Unit) {
    when (item.vaultItem) {
        is Password -> PasswordScreen(
            detailPaneInformation = item,
            navigate = navigate
        )

        is VaultItem.Basic,
        is VaultSearchResult -> throw IllegalArgumentException("Not supported")
    }
}