package de.davis.keygo.totp.data.repository

import de.davis.keygo.totp.domain.model.TotpInformation
import de.davis.keygo.totp.domain.repository.TotpGenerator
import dev.turingcomplete.kotlinonetimepassword.GoogleAuthenticator
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import org.koin.core.annotation.Single
import kotlin.time.Duration.Companion.seconds

@Single
class TotpGeneratorImpl : TotpGenerator {

    override fun observeTotp(encodedSecret: ByteArray): Flow<TotpInformation> {
        if (encodedSecret.isEmpty()) return flowOf()

        return flow {
            val generator = GoogleAuthenticator(base32secret = encodedSecret)
            val periodMs = 30.seconds.inWholeMilliseconds // Default used by GoogleAuthenticator

            while (currentCoroutineContext().isActive) {
                val totp = generator.generate()

                val now = System.currentTimeMillis()
                val nextWindowStart = ((now / periodMs) + 1) * periodMs
                val remaining = nextWindowStart - now

                emit(
                    TotpInformation(
                        code = totp,
                        validUntil = nextWindowStart,
                        maxLifetime = periodMs
                    )
                )
                delay(remaining)
            }
        }
    }
}