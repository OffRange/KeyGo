package de.davis.keygo.feature.item.core.domain.model

import de.davis.keygo.core.item.domain.model.Tag

interface UpsertItem {
    val upsertType: UpsertType
    val name: FieldUpdate<String>
    val tags: FieldUpdate<Set<Tag>>
    val note: FieldUpdate<String>
}
