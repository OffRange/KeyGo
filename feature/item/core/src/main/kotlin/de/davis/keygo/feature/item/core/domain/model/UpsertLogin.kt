package de.davis.keygo.feature.item.core.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.DomainInfo

@ConsistentCopyVisibility
data class UpsertLogin private constructor(
    val upsertType: UpsertType,
    val name: FieldUpdate<String>,
    val password: FieldUpdate<String>,
    val totoUriOrSecret: FieldUpdate<String>,
    val username: FieldUpdate<String>,
    val domains: FieldUpdate<Set<DomainInfo>>,
    val note: FieldUpdate<String>,
) {
    companion object {
        fun create(
            vaultId: VaultId,
            name: String,
            password: String? = null,
            totoUriOrSecret: String? = null,
            username: String? = null,
            domains: Set<DomainInfo> = emptySet(),
            note: String? = null,
        ) = UpsertLogin(
            upsertType = UpsertType.Create(vaultId),
            name = FieldUpdate.Set(name),
            password = if (!password.isNullOrBlank()) FieldUpdate.Set(password) else FieldUpdate.Clear,
            note = if (!note.isNullOrBlank()) FieldUpdate.Set(note) else FieldUpdate.Clear,
            totoUriOrSecret = if (!totoUriOrSecret.isNullOrBlank()) FieldUpdate.Set(totoUriOrSecret) else FieldUpdate.Clear,
            username = if (!username.isNullOrBlank()) FieldUpdate.Set(username) else FieldUpdate.Clear,
            domains = if (domains.isNotEmpty()) FieldUpdate.Set(domains) else FieldUpdate.Clear,
        )

        fun update(
            itemId: ItemId,
            vaultId: VaultId? = null,
            name: FieldUpdate<String> = keep(),
            password: FieldUpdate<String> = keep(),
            totoUriOrSecret: FieldUpdate<String> = keep(),
            username: FieldUpdate<String> = keep(),
            domains: FieldUpdate<Set<DomainInfo>> = keep(),
            note: FieldUpdate<String> = keep(),
        ) = UpsertLogin(
            upsertType = UpsertType.Update(itemId, vaultId),
            name = name,
            password = password,
            note = note,
            totoUriOrSecret = totoUriOrSecret,
            username = username,
            domains = domains,
        )
    }
}
