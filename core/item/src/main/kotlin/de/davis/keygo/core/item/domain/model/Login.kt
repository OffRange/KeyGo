package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.processor.annotation.VaultEntity

@VaultEntity(resString = "password", defaultIconType = "Password")
data class Login(
    override val id: ItemId = newItemId(),
    val username: String?,
    val domainInfos: Set<DomainInfo>,
    val passwordScore: PasswordScore,
    val password: SecretData<String>,
    val totp: Totp?,
    val passkeyRPs: Set<String> = emptySet(),
    override val vaultId: VaultId,
    override val name: String,
    override val keyInformation: KeyInformation,
    override val note: String?,
    override val pinned: Boolean,
) : Item {

    override val itemType: VaultItemType
        get() = VaultItemType.Login

    companion object {
        const val LABEL_PASSWORD = "password"
        const val LABEL_TOTP_SECRET = "totp_secret"
    }
}
