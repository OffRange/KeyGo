package de.davis.keygo.item.domain.usecase

import de.davis.keygo.item.domain.model.Strength
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.gosimple.nbvcxz.Nbvcxz

class EstimatePasswordStrengthUseCase(private val nbvcxz: Nbvcxz) {

    suspend operator fun invoke(password: String): Strength = withContext(Dispatchers.Default) {
        if (password.isEmpty())
            return@withContext Strength.None

        nbvcxz.estimate(password).let { estimate ->
            Strength(
                Strength.Score(estimate.basicScore + 1 /* 1..5 */),
                estimate.feedback.suggestion
            )
        }
    }
}