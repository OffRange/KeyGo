package de.davis.keygo.core.data.estimator

import de.davis.keygo.core.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.Password
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.gosimple.nbvcxz.Nbvcxz
import org.koin.core.annotation.Single

@Single
class PasswordStrengthEstimatorImpl(private val nbvcxz: Nbvcxz) : PasswordStrengthEstimator {

    override suspend fun estimate(password: String): Password.Score =
        withContext(Dispatchers.Default) {
            if (password.isEmpty())
                return@withContext Password.Score.None

            val result = nbvcxz.estimate(password)
            Password.Score(result.basicScore + 1 /* 1..5 */)
        }
}