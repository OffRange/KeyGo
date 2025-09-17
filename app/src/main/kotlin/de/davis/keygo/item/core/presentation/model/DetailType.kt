package de.davis.keygo.item.core.presentation.model

import android.os.Parcelable
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
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
            override val vaultItemType: VaultItemType = VaultItemType.Password
        }
    }

    data class View(val itemId: ItemId) : DetailType
}

fun DetailType.asDetailPaneInformation() = DetailPaneInformation.InitByDetailType(this)