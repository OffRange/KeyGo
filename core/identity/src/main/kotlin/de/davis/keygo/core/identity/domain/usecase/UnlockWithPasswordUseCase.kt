package de.davis.keygo.core.identity.domain.usecase

import android.security.keystore.KeyProperties
import de.davis.keygo.core.identity.domain.model.PasswordWrappedKeyData
import de.davis.keygo.core.identity.domain.model.UnlockError
import de.davis.keygo.core.identity.domain.repository.DeviceInfoRepository
import de.davis.keygo.core.identity.domain.repository.KeyDerivationRepository
import de.davis.keygo.core.identity.domain.repository.WrappedKeyRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.model.asAesKey
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import org.koin.core.annotation.Single
import java.security.Key
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Single
class UnlockWithPasswordUseCase(
    private val session: Session,
    private val wrappedKeyRepository: WrappedKeyRepository,
    private val keyDerivationRepository: KeyDerivationRepository,
    private val deviceInfoRepository: DeviceInfoRepository
) {

    suspend operator fun invoke(password: String): Result<Unit, UnlockError> {
        val wrappedKey = wrappedKeyRepository.getPasswordWrappedKey().getOrNull()
            ?: return Result.Failure(UnlockError.WrappedKeyNotFound)

        val derivedKey = keyDerivationRepository.deriveKey(
            password = password.toByteArray(),
            salt = wrappedKey.salt,
            parallelism = deviceInfoRepository.getNumCors(),
        ).getOrNull()?.let {
            SecretKeySpec(it, 0, it.size, "AES")
        } ?: return Result.Failure(UnlockError.DerivationFailed)

        val key = wrappedKey.unwrapUsing(derivedKey)
            ?: return Result.Failure(UnlockError.UnwrappingFailed)

        session.startSession(key.asAesKey())
        return Result.Success(Unit)
    }

    private fun PasswordWrappedKeyData.unwrapUsing(key: Key): Key? {
        val cipher = Cipher.getInstance(
            "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
        )
        cipher.init(Cipher.UNWRAP_MODE, key, GCMParameterSpec(AUTH_TAG_LENGTH, this.keyIV))

        return runCatching {
            cipher.unwrap(this.key, "AES", Cipher.SECRET_KEY)
        }.getOrNull()
    }


    private companion object {
        private const val AUTH_TAG_LENGTH = 128
    }
}
