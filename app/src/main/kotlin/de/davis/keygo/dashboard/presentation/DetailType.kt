package de.davis.keygo.dashboard.presentation

import android.os.Parcelable
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.feature.item.core.presentation.model.DetailPaneInformation
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface DetailType : Parcelable {

    @Parcelize
    sealed interface Modify : DetailType {
        val vaultItemType: VaultItemType

        data class CreateNew(override val vaultItemType: VaultItemType) : Modify
        data class Edit(override val vaultItemType: VaultItemType, val itemId: ItemId) : Modify
        data class Totp(val uri: String) : Modify {

            @IgnoredOnParcel
            override val vaultItemType: VaultItemType = VaultItemType.Login
        }
    }

    data class View(val itemId: ItemId) : DetailType
}

fun DetailType.Modify.asDetailPaneInformation() = when (this) {
    is DetailType.Modify.CreateNew -> DetailPaneInformation.Init.New(vaultItemType)
    is DetailType.Modify.Edit -> DetailPaneInformation.Init.Existing(vaultItemType, itemId)
    is DetailType.Modify.Totp -> DetailPaneInformation.Init.TOTP(vaultItemType, uri)
}