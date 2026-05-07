package de.davis.keygo.core.item

import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.PasswordScore

/**
 * [PasswordStrengthEstimator] fake that always returns [de.davis.keygo.core.item.domain.model.PasswordScore.Strong].
 * Inject a custom [passwordScore] when score-specific behaviour needs asserting.
 */
class FakePasswordStrengthEstimator(
    val passwordScore: PasswordScore = PasswordScore.Strong,
) : PasswordStrengthEstimator {
    override suspend fun estimate(password: String): PasswordScore = passwordScore
}