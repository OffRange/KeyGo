package de.davis.keygo.core.item.domain.estimator

import de.davis.keygo.core.item.domain.model.PasswordScore

interface PasswordStrengthEstimator {

    suspend fun estimate(password: String): PasswordScore

    suspend operator fun invoke(password: String): PasswordScore = estimate(password)
}
