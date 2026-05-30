package de.davis.keygo.feature.credit_card.domain.repository

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.credit_card.domain.ApduTransport
import de.davis.keygo.feature.credit_card.domain.model.Card
import de.davis.keygo.feature.credit_card.domain.model.CardReadFailure
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

interface CardReaderRepository {

    suspend fun read(
        transport: ApduTransport,
        context: CoroutineContext = Dispatchers.IO
    ): Result<Card, CardReadFailure>
}