package de.davis.keygo.core.domain.model.navigation

import de.davis.keygo.core.domain.model.VaultItem
import de.davis.keygo.generated.item.VaultItemEnum

// Todo(b/378882434): Configuration changes will cause a crash as android can not save the state yet
sealed interface DetailItem {
    data class Edit(val type: VaultItemEnum) : DetailItem
    data class View(val item: VaultItem) : DetailItem
}