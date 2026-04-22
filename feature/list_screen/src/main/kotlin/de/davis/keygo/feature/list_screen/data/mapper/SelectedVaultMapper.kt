package de.davis.keygo.feature.list_screen.data.mapper

import de.davis.keygo.feature.list_screen.data.local.model.ProtoListSelection
import de.davis.keygo.feature.list_screen.data.local.model.protoListSelection
import de.davis.keygo.feature.list_screen.domain.model.SelectedVault
import java.util.UUID

internal fun ProtoListSelection.toDomain(): SelectedVault {
    if (!hasSelectedVaultId()) return SelectedVault.All
    return SelectedVault.Id(UUID.fromString(selectedVaultId))
}

internal fun SelectedVault.toProto(): ProtoListSelection = protoListSelection {
    when (this@toProto) {
        SelectedVault.All -> clearSelectedVaultId()
        is SelectedVault.Id -> selectedVaultId = vaultId.toString()
    }
}
