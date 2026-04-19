package de.davis.keygo.core.item.data.local.pojo

import androidx.room.Embedded
import de.davis.keygo.core.item.data.local.entity.KeyInformation
import de.davis.keygo.core.item.domain.alias.VaultId

internal data class ActiveVaultKeyInformation(
    @Embedded
    val keyInformation: KeyInformation,
    val vaultId: VaultId
)
