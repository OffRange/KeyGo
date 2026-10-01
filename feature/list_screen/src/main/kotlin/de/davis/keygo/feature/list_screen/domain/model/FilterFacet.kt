package de.davis.keygo.feature.list_screen.domain.model

import de.davis.keygo.core.item.domain.model.CardExpiryStatus
import de.davis.keygo.core.item.domain.model.CredentialType
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.model.lite.LiteItem
import de.davis.keygo.core.item.generated.domain.model.VaultItemType

sealed interface FilterFacet<T : Any> {

    // An item of a type the facet does not apply to passes its filter and offers none of its values.
    fun appliesTo(type: VaultItemType): Boolean = true

    fun valuesFor(item: LiteItem, attributes: ItemAttributes): Set<T>

    data object ItemTypes : FilterFacet<VaultItemType> {
        override fun valuesFor(item: LiteItem, attributes: ItemAttributes) = setOf(item.itemType)
    }

    data object Tags : FilterFacet<Tag> {
        override fun valuesFor(item: LiteItem, attributes: ItemAttributes) =
            attributes.tagsByItem[item.id].orEmpty()
    }

    data object PasswordScores : FilterFacet<PasswordScore> {
        override fun appliesTo(type: VaultItemType) = type == VaultItemType.Login

        override fun valuesFor(item: LiteItem, attributes: ItemAttributes) =
            setOfNotNull(attributes.passwordScoreByItem[item.id])
    }

    data object Credentials : FilterFacet<CredentialType> {
        override fun appliesTo(type: VaultItemType) = type == VaultItemType.Login

        override fun valuesFor(item: LiteItem, attributes: ItemAttributes) =
            attributes.credentialsByItem[item.id].orEmpty()
    }

    data object Pinned : FilterFacet<Boolean> {
        override fun valuesFor(item: LiteItem, attributes: ItemAttributes) =
            if (item.pinned) setOf(true) else emptySet()
    }

    data object CardExpiryStatuses : FilterFacet<CardExpiryStatus> {
        override fun appliesTo(type: VaultItemType): Boolean = type == VaultItemType.CreditCard

        override fun valuesFor(item: LiteItem, attributes: ItemAttributes) =
            setOfNotNull(attributes.cardExpiryStatusByItem[item.id])
    }

    companion object {
        // A getter, not a stored list: the interface initializes while its objects still are, so an
        // eager list would capture a null.
        val entries: List<FilterFacet<*>>
            get() = listOf(ItemTypes, Tags, PasswordScores, Credentials, Pinned, CardExpiryStatuses)
    }
}
