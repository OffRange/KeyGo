package de.davis.keygo.core.item.domain.estimator

import de.davis.keygo.core.item.domain.model.Password

interface PasswordStrengthEstimator {

    suspend fun estimate(password: String): Password.Score

    suspend operator fun invoke(password: String): Password.Score = estimate(password)
}
