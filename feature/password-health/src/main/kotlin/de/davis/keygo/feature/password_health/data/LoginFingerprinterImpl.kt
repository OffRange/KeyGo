package de.davis.keygo.feature.password_health.data

import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.feature.password_health.domain.LoginFingerprinter
import de.davis.keygo.feature.password_health.domain.model.HealthFingerprint
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.coroutines.CoroutineContext
import kotlin.uuid.toKotlinUuid

@Single
internal class LoginFingerprinterImpl : LoginFingerprinter {

    override suspend fun fingerprint(
        login: Login,
        coroutineContext: CoroutineContext
    ): HealthFingerprint? = withContext(coroutineContext) {
        val payload = login.passwordCredential?.secret?.payload ?: return@withContext null

        val digest = MessageDigest.getInstance("SHA-256")

        digest.update(login.id.toKotlinUuid().toByteArray())
        digest.updateWithPrefix(payload.ciphertext)
        digest.updateWithPrefix(payload.iv)

        HealthFingerprint(digest.digest())
    }

    private fun MessageDigest.updateWithPrefix(data: ByteArray) {
        update(
            ByteBuffer.allocate(LENGTH_PREFIX_BYTES)
                .putInt(data.size)
                .array(),
        )
        update(data)
    }

    companion object {
        private const val LENGTH_PREFIX_BYTES = 4
    }
}
