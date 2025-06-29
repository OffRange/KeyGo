package de.davis.keygo.core.data.estimator

import de.davis.keygo.core.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.domain.model.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.gosimple.nbvcxz.Nbvcxz
import org.koin.core.annotation.Single

@Single
class PasswordStrengthEstimatorImpl(private val nbvcxz: Nbvcxz) : PasswordStrengthEstimator {

    override suspend fun estimate(password: String): Score = withContext(Dispatchers.Default) {
        if (password.isEmpty())
            return@withContext Score.None

        val result = nbvcxz.estimate(password)
        Score(result.basicScore + 1 /* 1..5 */)
    }
}