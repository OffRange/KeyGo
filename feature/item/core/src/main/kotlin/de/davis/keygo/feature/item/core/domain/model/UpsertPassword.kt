package de.davis.keygo.feature.item.core.domain.model

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.model.DomainInfo

@ConsistentCopyVisibility
data class UpsertPassword private constructor(
    val upsertType: UpsertType,
    val name: FieldUpdate<String>,
    val password: FieldUpdate<String>,
    val totpSecret: FieldUpdate<String>,
    val username: FieldUpdate<String>,
    val domains: FieldUpdate<Set<DomainInfo>>,
    val note: FieldUpdate<String>,
) {
    companion object {
        fun create(
            name: String,
            password: String,
            totpSecret: String? = null,
            username: String? = null,
            domains: Set<DomainInfo> = emptySet(),
            note: String? = null,
        ) = UpsertPassword(
            upsertType = UpsertType.Create,
            name = FieldUpdate.Set(name),
            password = FieldUpdate.Set(password),
            note = if (!note.isNullOrBlank()) FieldUpdate.Set(note) else FieldUpdate.Clear,
            totpSecret = if (!totpSecret.isNullOrBlank()) FieldUpdate.Set(totpSecret) else FieldUpdate.Clear,
            username = if (!username.isNullOrBlank()) FieldUpdate.Set(username) else FieldUpdate.Clear,
            domains = if (domains.isNotEmpty()) FieldUpdate.Set(domains) else FieldUpdate.Clear,
        )

        fun update(
            vaultId: ItemId,
            name: FieldUpdate<String> = keep(),
            password: FieldUpdate<String> = keep(),
            totpSecret: FieldUpdate<String> = keep(),
            username: FieldUpdate<String> = keep(),
            domains: FieldUpdate<Set<DomainInfo>> = keep(),
            note: FieldUpdate<String> = keep(),
        ) = UpsertPassword(
            upsertType = UpsertType.Update(vaultId),
            name = name,
            password = password,
            note = note,
            totpSecret = totpSecret,
            username = username,
            domains = domains,
        )
    }
}