package de.davis.keygo.core.item.domain.estimator

import de.davis.keygo.core.item.domain.model.Login

interface PasswordStrengthEstimator {

    suspend fun estimate(password: String): Login.Score

    suspend operator fun invoke(password: String): Login.Score = estimate(password)
}
