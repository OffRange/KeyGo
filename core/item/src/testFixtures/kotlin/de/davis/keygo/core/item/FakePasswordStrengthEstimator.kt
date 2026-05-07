package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.Login

/**
 * [PasswordStrengthEstimator] fake that always returns [Login.Score.Strong].
 * Inject a custom [score] when score-specific behaviour needs asserting.
 */
class FakePasswordStrengthEstimator(
    val score: Login.Score = Login.Score.Strong,
) : PasswordStrengthEstimator {
    override suspend fun estimate(password: String): Login.Score = score
}