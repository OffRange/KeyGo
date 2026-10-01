package de.davis.keygo.feature.item.core.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.PasskeyRef
import de.davis.keygo.core.item.domain.model.Tag

@ConsistentCopyVisibility
data class UpsertLogin private constructor(
    override val upsertType: UpsertType,
    override val name: FieldUpdate<String>,
    val password: FieldUpdate<String>,
    val totpUriOrSecret: FieldUpdate<String>,
    val username: FieldUpdate<String>,
    override val tags: FieldUpdate<Set<Tag>>,
    override val note: FieldUpdate<String>,
    val removedPasskeys: Set<PasskeyRef>,
    val addedPasskeys: Set<NewPasskey>,
    val removedDomains: Set<String>,
    val addedDomains: Set<String>,
) : UpsertItem {
    companion object {
        fun create(
            vaultId: VaultId,
            name: String,
            password: String? = null,
            totpUriOrSecret: String? = null,
            username: String? = null,
            domains: Set<String> = emptySet(),
            tags: Set<Tag> = emptySet(),
            note: String? = null,
            addedPasskeys: Set<NewPasskey> = emptySet(),
        ) = UpsertLogin(
            upsertType = UpsertType.Create(vaultId),
            name = FieldUpdate.Set(name),
            password = if (!password.isNullOrBlank()) FieldUpdate.Set(password) else FieldUpdate.Clear,
            note = if (!note.isNullOrBlank()) FieldUpdate.Set(note) else FieldUpdate.Clear,
            totpUriOrSecret = if (!totpUriOrSecret.isNullOrBlank()) FieldUpdate.Set(totpUriOrSecret) else FieldUpdate.Clear,
            username = if (!username.isNullOrBlank()) FieldUpdate.Set(username) else FieldUpdate.Clear,
            tags = if (tags.isNotEmpty()) FieldUpdate.Set(tags) else FieldUpdate.Clear,
            // A brand-new login holds no passkeys, so there is nothing to remove.
            removedPasskeys = emptySet(),
            addedPasskeys = addedPasskeys,
            removedDomains = emptySet(),
            addedDomains = domains,
        )

        fun update(
            itemId: ItemId,
            vaultId: VaultId? = null,
            name: FieldUpdate<String> = keep(),
            password: FieldUpdate<String> = keep(),
            totpUriOrSecret: FieldUpdate<String> = keep(),
            username: FieldUpdate<String> = keep(),
            tags: FieldUpdate<Set<Tag>> = keep(),
            note: FieldUpdate<String> = keep(),
            removedPasskeys: Set<PasskeyRef> = emptySet(),
            addedPasskeys: Set<NewPasskey> = emptySet(),
            removedDomains: Set<String> = emptySet(),
            addedDomains: Set<String> = emptySet(),
        ) = UpsertLogin(
            upsertType = UpsertType.Update(itemId, vaultId),
            name = name,
            password = password,
            note = note,
            totpUriOrSecret = totpUriOrSecret,
            tags = tags,
            username = username,
            removedPasskeys = removedPasskeys,
            addedPasskeys = addedPasskeys,
            removedDomains = removedDomains,
            addedDomains = addedDomains,
        )
    }
}
