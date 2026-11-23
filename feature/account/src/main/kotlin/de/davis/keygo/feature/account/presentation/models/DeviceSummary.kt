package de.davis.keygo.feature.account.presentation.models

import de.davis.keygo.core.sync.domain.model.Device

internal data class DeviceSummary(
    val total: Int,
    val signedIn: Int,
)

internal fun List<Device>.toSummary() = DeviceSummary(
    total = size,
    signedIn = count { it.loggedIn },
)
