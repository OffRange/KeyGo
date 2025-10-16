package de.davis.keygo.core.item.domain.model

import androidx.annotation.IntRange
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.processor.annotation.VaultEntity

@VaultEntity(resString = "password", defaultIconType = "Password")
data class Password(
    val id: ItemId = 0,
    val username: String?,
    val domainInfos: Set<DomainInfo>,
    val score: Score,
    val totpSecret: SecretData<String>?,
    override val vaultItemId: ItemId = 0,
    override val name: String,
    override val encryptedData: SecretData<String>,
    override val note: String?
) : VaultItem {

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
            operator fun invoke(@IntRange(from = 1, to = 5) value: Int): Score =
                when (value) {
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