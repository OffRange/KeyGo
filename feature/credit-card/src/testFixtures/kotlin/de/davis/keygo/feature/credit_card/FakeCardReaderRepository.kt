package de.davis.keygo.feature.credit_card

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.credit_card.domain.ApduTransport
import de.davis.keygo.feature.credit_card.domain.model.Card
import de.davis.keygo.feature.credit_card.domain.model.CardReadFailure
import de.davis.keygo.feature.credit_card.domain.repository.CardReaderRepository
import kotlin.coroutines.CoroutineContext

/** In-memory [CardReaderRepository] for tests. Returns [result] and closes the transport. */
class FakeCardReaderRepository(
    var result: Result<Card, CardReadFailure>,
) : CardReaderRepository {

    var readCount: Int = 0
        private set

    override suspend fun read(
        transport: ApduTransport,
        context: CoroutineContext,
    ): Result<Card, CardReadFailure> {
        readCount++
        transport.close()
        return result
    }
}
