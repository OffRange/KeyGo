package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.Password

/**
 * [PasswordStrengthEstimator] fake that always returns [Password.Score.Strong].
 * Inject a custom [score] when score-specific behaviour needs asserting.
 */
class FakePasswordStrengthEstimator(
    val score: Password.Score = Password.Score.Strong,
) : PasswordStrengthEstimator {
    override suspend fun estimate(password: String): Password.Score = score
}