package de.davis.keygo.core.item.data.estimator

import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.PasswordScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.gosimple.nbvcxz.Nbvcxz
import org.koin.core.annotation.Single

@Single
internal class PasswordStrengthEstimatorImpl(private val nbvcxz: Nbvcxz) :
    PasswordStrengthEstimator {

    override suspend fun estimate(password: String): PasswordScore =
        withContext(Dispatchers.Default) {
            if (password.isEmpty())
                return@withContext PasswordScore.None

            val result = nbvcxz.estimate(password)
            PasswordScore(result.basicScore + 1 /* 1..5 */)
        }
}