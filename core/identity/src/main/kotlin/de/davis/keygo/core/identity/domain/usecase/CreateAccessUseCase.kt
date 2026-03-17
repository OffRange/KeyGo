package de.davis.keygo.core.identity.domain.usecase

import android.security.keystore.KeyProperties
import de.davis.keygo.core.identity.domain.model.BiometricWrappedKeyData
import de.davis.keygo.core.identity.domain.model.CreateAccessError
import de.davis.keygo.core.identity.domain.model.PasswordWrappedKeyData
import de.davis.keygo.core.identity.domain.repository.DeviceInfoRepository
import de.davis.keygo.core.identity.domain.repository.KeyDerivationRepository
import de.davis.keygo.core.identity.domain.repository.WrappedKeyRepository
import de.davis.keygo.core.security.domain.Session
import de.davis.keygo.core.security.domain.crypto.model.asAesKey
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.getOrNull
import org.koin.core.annotation.Single
import java.security.Key
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.SecretKeySpec


@Single
class CreateAccessUseCase(
    private val keyDerivationRepository: KeyDerivationRepository,
    private val deviceInfoRepository: DeviceInfoRepository,
    private val wrappedKeyRepository: WrappedKeyRepository,
    private val session: Session
) {

    /**
     * Use case to create access by generating a new Data Encryption Key (DEK), that is then wrapped
     * with a Key Encryption Key (KEK) derived from the user's password. Optionally, the DEK can also be
     * wrapped with a KEK derived from biometric data.
     *
     * The generated DEK is stored in the session for immediate use. The password-wrapped DEK and,
     * if applicable, the biometric-wrapped DEK are stored in the [WrappedKeyRepository] for future
     * retrieval.
     *
     * @param password The user's password used to derive the KEK for wrapping the DEK.
     * @param biometricCipher An optional [Cipher] initialized for wrapping the DEK with biometric data.
     */
    suspend operator fun invoke(
        password: String,
        biometricCipher: Cipher? = null
    ): Result<Unit, CreateAccessError> = KeyGenerator.getInstance("AES").apply {
        init(256)
    }.generateKey().let { keyToWrap ->
        val random = SecureRandom()
        val salt = ByteArray(16)
        random.nextBytes(salt)

        val derivedKek = keyDerivationRepository.deriveKey(
            password = password.toByteArray(),
            salt = salt,
            parallelism = deviceInfoRepository.getNumCors()
        ).getOrNull()?.let {
            SecretKeySpec(it, 0, it.size, "AES")
        } ?: return Result.Failure(CreateAccessError.KeyDerivationFailed)

        keyToWrap.wrapUsing(derivedKek)?.let { (passwordWrappedDek, iv) ->
            wrappedKeyRepository.setPasswordWrappedKey(
                PasswordWrappedKeyData(
                    key = passwordWrappedDek,
                    keyIV = iv,
                    salt = salt
                )
            )
        } ?: return Result.Failure(CreateAccessError.WrappingFailed)


        if (biometricCipher == null) {
            session.startSession(keyToWrap.asAesKey())
            return Result.Success(Unit)
        }

        keyToWrap.wrapUsingCipher(biometricCipher)?.let { (biometricWrappedDek, iv) ->
            wrappedKeyRepository.setBiometricWrappedKey(
                BiometricWrappedKeyData(
                    key = biometricWrappedDek,
                    keyIV = iv
                )
            )
        } ?: return Result.Failure(CreateAccessError.WrappingFailed)

        session.startSession(keyToWrap.asAesKey())
        return Result.Success(Unit)
    }

    private fun Key.wrapUsing(key: Key): Pair<ByteArray, ByteArray>? {
        val cipher = Cipher.getInstance(
            "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
        )
        cipher.init(Cipher.WRAP_MODE, key)
        return wrapUsingCipher(cipher)
    }

    private fun Key.wrapUsingCipher(cipher: Cipher): Pair<ByteArray, ByteArray>? {
        return runCatching {
            cipher.wrap(this) to cipher.iv
        }.getOrNull()
    }
}
