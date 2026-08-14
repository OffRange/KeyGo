package de.davis.keygo.feature.item.core.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.Tag

@ConsistentCopyVisibility
data class UpsertLogin private constructor(
    override val upsertType: UpsertType,
    override val name: FieldUpdate<String>,
    val password: FieldUpdate<String>,
    val totpUriOrSecret: FieldUpdate<String>,
    val username: FieldUpdate<String>,
    val domains: FieldUpdate<Set<DomainInfo>>,
    override val tags: FieldUpdate<Set<Tag>>,
    override val note: FieldUpdate<String>,
) : UpsertItem {
    companion object {
        fun create(
            vaultId: VaultId,
            name: String,
            password: String? = null,
            totpUriOrSecret: String? = null,
            username: String? = null,
            domains: Set<DomainInfo> = emptySet(),
            tags: Set<Tag> = emptySet(),
            note: String? = null,
        ) = UpsertLogin(
            upsertType = UpsertType.Create(vaultId),
            name = FieldUpdate.Set(name),
            password = if (!password.isNullOrBlank()) FieldUpdate.Set(password) else FieldUpdate.Clear,
            note = if (!note.isNullOrBlank()) FieldUpdate.Set(note) else FieldUpdate.Clear,
            totpUriOrSecret = if (!totpUriOrSecret.isNullOrBlank()) FieldUpdate.Set(totpUriOrSecret) else FieldUpdate.Clear,
            username = if (!username.isNullOrBlank()) FieldUpdate.Set(username) else FieldUpdate.Clear,
            domains = if (domains.isNotEmpty()) FieldUpdate.Set(domains) else FieldUpdate.Clear,
            tags = if (tags.isNotEmpty()) FieldUpdate.Set(tags) else FieldUpdate.Clear,
        )

        fun update(
            itemId: ItemId,
            vaultId: VaultId? = null,
            name: FieldUpdate<String> = keep(),
            password: FieldUpdate<String> = keep(),
            totpUriOrSecret: FieldUpdate<String> = keep(),
            username: FieldUpdate<String> = keep(),
            domains: FieldUpdate<Set<DomainInfo>> = keep(),
            tags: FieldUpdate<Set<Tag>> = keep(),
            note: FieldUpdate<String> = keep(),
        ) = UpsertLogin(
            upsertType = UpsertType.Update(itemId, vaultId),
            name = name,
            password = password,
            note = note,
            totpUriOrSecret = totpUriOrSecret,
            tags = tags,
            username = username,
            domains = domains,
        )
    }
}
