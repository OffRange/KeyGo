package de.davis.keygo.feature.credit_card.data.repository

import com.github.devnied.emvnfccard.parser.EmvTemplate
import com.github.devnied.emvnfccard.parser.IProvider
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.credit_card.data.mapper.toDomain
import de.davis.keygo.feature.credit_card.domain.ApduTransport
import de.davis.keygo.feature.credit_card.domain.exception.CardRemovedException
import de.davis.keygo.feature.credit_card.domain.model.Card
import de.davis.keygo.feature.credit_card.domain.model.CardReadFailure
import de.davis.keygo.feature.credit_card.domain.repository.CardReaderRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import kotlin.coroutines.CoroutineContext

@Single
internal class CardReaderRepositoryImpl : CardReaderRepository {

    override suspend fun read(
        transport: ApduTransport,
        context: CoroutineContext
    ): Result<Card, CardReadFailure> = withContext(context) {
        transport.use { channel ->
            try {
                parser(channel).readEmvCard().toDomain()
                    ?.let { Result.Success(it) }
                    ?: Result.Failure(CardReadFailure.NoReadableData)
            } catch (e: CancellationException) {
                throw e
            } catch (_: CardRemovedException) {
                Result.Failure(CardReadFailure.TagLost)
            } catch (e: Exception) {
                Result.Failure(CardReadFailure.Unexpected(e))
            }
        }
    }

    private fun parser(transport: ApduTransport): EmvTemplate {
        val provider = object : IProvider {
            override fun transceive(command: ByteArray): ByteArray = transport.transceive(command)
            override fun getAt(): ByteArray = transport.historicalBytes
        }

        val config = EmvTemplate.Config()
            .setContactLess(true)
            .setReadAllAids(true)
            .setReadTransactions(false)
            .setReadAt(true)
            .setRemoveDefaultParsers(false)

        return EmvTemplate.Builder()
            .setProvider(provider)
            .setConfig(config)
            .build()
    }
}