package de.davis.keygo.feature.password_health.data.mapper

import de.davis.keygo.feature.password_health.data.local.model.ProtoBreachCheckState
import de.davis.keygo.feature.password_health.domain.model.BreachCheckState

internal fun ProtoBreachCheckState.toDomain() = BreachCheckState(
    enabled = enabled
)
