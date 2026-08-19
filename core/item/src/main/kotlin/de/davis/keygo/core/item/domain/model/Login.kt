package de.davis.keygo.core.item.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import de.davis.keygo.processor.annotation.VaultEntity

@VaultEntity(resString = "login", defaultIconType = "Password")
data class Login(
    override val id: ItemId = newItemId(),
    val username: String?,
    val domainInfos: Set<DomainInfo>,
    val passwordCredential: PasswordCredential?,
    val totp: Totp?,
    /**
     * Passkeys this login currently holds, one entry per credential.
     *
     * Has no default and is never inferred: an empty set means "this login holds no passkeys" and
     * saving it deletes every passkey row the login has. A caller that does not know has to read
     * them rather than leave them out.
     */
    val passkeys: Set<PasskeyRef>,
    override val vaultId: VaultId,
    override val name: String,
    override val keyInformation: KeyInformation,
    override val timestamp: Timestamp,
    override val tags: Set<Tag> = emptySet(),
    override val note: String?,
    override val pinned: Boolean,
) : Item {

    override val itemType: VaultItemType
        get() = VaultItemType.Login

    val hasAnyContent: Boolean
        get() = !username.isNullOrBlank()
                || passwordCredential != null
                || totp != null
                || passkeys.isNotEmpty()
}
