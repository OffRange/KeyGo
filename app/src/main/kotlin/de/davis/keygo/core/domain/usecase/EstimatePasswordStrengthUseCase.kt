package de.davis.keygo.core.domain.usecase

import de.davis.keygo.core.domain.model.Score
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.gosimple.nbvcxz.Nbvcxz
import org.koin.core.annotation.Single

@Single
class EstimatePasswordStrengthUseCase(private val nbvcxz: Nbvcxz) {

    suspend operator fun invoke(password: String): Score = withContext(Dispatchers.Default) {
        if (password.isEmpty())
            return@withContext Score.None

        val result = nbvcxz.estimate(password)
        Score(result.basicScore + 1 /* 1..5 */)
    }
}