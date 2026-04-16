package de.davis.keygo.core.item.domain.model

import androidx.annotation.IntRange
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.processor.annotation.VaultEntity

@VaultEntity(resString = "password", defaultIconType = "Password")
data class Password(
    override val id: ItemId = newItemId(),
    val username: String?,
    val domainInfos: Set<DomainInfo>,
    val score: Score,
    val password: SecretData<String>,
    val totpSecret: SecretData<String>?,
    val passkeyRPs: Set<String> = emptySet(),
    override val vaultId: VaultId,
    override val name: String,
    override val keyInformation: KeyInformation,
    override val note: String?,
    override val pinned: Boolean,
) : Item {

    override val itemType: VaultItemType
        get() = VaultItemType.Password

    enum class Score {
        None,
        Ridiculous,
        Weak,
        Moderate,
        Strong,
        Excellent;

        val isNone: Boolean
            get() = this == None

        companion object {
            operator fun invoke(@IntRange(from = 1, to = 5) value: Int): Score = when (value) {
                1 -> Ridiculous
                2 -> Weak
                3 -> Moderate
                4 -> Strong
                5 -> Excellent
                else -> None
            }
        }
    }
}
