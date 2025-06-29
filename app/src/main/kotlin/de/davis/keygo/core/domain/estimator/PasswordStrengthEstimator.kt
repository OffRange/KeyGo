package de.davis.keygo.core.domain.estimator

import de.davis.keygo.core.domain.model.Score

interface PasswordStrengthEstimator {

    suspend fun estimate(password: String): Score

    suspend operator fun invoke(password: String): Score = estimate(password)
}
