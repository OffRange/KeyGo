package de.davis.keygo.feature.item.core.presentation.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import kotlinx.serialization.Serializable

sealed interface DetailPaneInformation {
    sealed interface Init : DetailPaneInformation {
        val itemType: VaultItemType

        /**
         * @param pendingTotpUri a scanned code the form should fold in once it is built. Null for
         * an ordinary create or edit.
         */
        data class New(
            override val itemType: VaultItemType,
            val pendingTotpUri: String? = null,
        ) : Init

        data class Existing(
            override val itemType: VaultItemType,
            val id: ItemId,
            val pendingTotpUri: String? = null,
        ) : Init

        data class TOTP(override val itemType: VaultItemType, val uri: String) : Init
    }

    @Serializable
    sealed interface CreateRaw : DetailPaneInformation {
        val name: String

        @Serializable
        data class Login(
            override val name: String,
            val password: String,
            val username: String,
            val url: String?,
        ) : CreateRaw
    }
}