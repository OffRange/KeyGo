package de.davis.keygo.auth.domain.usecase

import de.davis.keygo.auth.domain.model.PasswordWrappedKeyData
import de.davis.keygo.auth.domain.repository.DeviceInfoRepository
import de.davis.keygo.auth.domain.repository.KeyDerivationRepository
import de.davis.keygo.auth.domain.repository.PasswordWrappedKeyRepository
import de.davis.keygo.core.di.annotation.BiometricQualifier
import de.davis.keygo.core.di.annotation.PasswordQualifier
import de.davis.keygo.core.domain.Session
import de.davis.keygo.core.domain.model.crypto.asAesKey
import de.davis.keygo.core.identity.common.domain.CipherFactory
import de.davis.keygo.core.identity.common.domain.model.BiometricWrappedKeyData
import de.davis.keygo.core.identity.common.domain.model.CryptographicMode
import de.davis.keygo.core.identity.common.domain.model.CryptographyError
import de.davis.keygo.core.identity.common.domain.repository.BiometricWrappedKeyRepository
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.asUnitResult
import de.davis.keygo.core.util.onSuccess
import de.davis.keygo.core.util.zip
import org.koin.core.annotation.Single
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator

@Single
class CreateAccessUseCase(
    private val cipherFactory: CipherFactory,
    private val keyDerivationRepository: KeyDerivationRepository,
    private val deviceInfoRepository: DeviceInfoRepository,
    @PasswordQualifier
    private val passwordWrappedKeyRepository: PasswordWrappedKeyRepository,
    @BiometricQualifier
    private val biometricWrappedKeyRepository: BiometricWrappedKeyRepository,
    private val session: Session
) {

    suspend operator fun invoke(
        password: String,
        biometricCipher: Cipher? = null
    ): Result<Unit, CryptographyError> = KeyGenerator.getInstance("AES").apply {
        init(256)
    }.generateKey()
        .asAesKey()
        .let { keyToWrap ->
            val random = SecureRandom()
            val salt = ByteArray(16)
            random.nextBytes(salt)

            keyDerivationRepository.deriveKey(
                password = password.toByteArray(),
                salt = salt,
                parallelism = deviceInfoRepository.getNumCors()
            ).zip { passwordKek ->
                cipherFactory.prepareCipher(
                    mode = CryptographicMode.Wrap,
                    kek = passwordKek.asAesKey()
                )
            }.zip { _, passwordCipher ->
                cipherFactory.wrapDataKey(
                    cipher = passwordCipher,
                    keyToWrap = keyToWrap
                )
            }.onSuccess { (_, passwordCipher, wrappedKey) ->
                passwordWrappedKeyRepository.setWrappedKeyData(
                    PasswordWrappedKeyData(
                        wrappedKey = wrappedKey,
                        salt = salt,
                        iv = passwordCipher.iv
                    )
                )
            }.apply {
                println("Password wrapped key data set successfully.")
                if (biometricCipher == null) return@apply
                println("Biometric cipher provided, proceeding with biometric key wrapping.")
                zip { _, _, _ ->
                    cipherFactory.wrapDataKey(
                        cipher = biometricCipher,
                        keyToWrap = keyToWrap
                    )
                }.onSuccess { (_, _, _, wrappedKey) ->
                    println("Biometric wrapped key data set successfully: $wrappedKey")
                    biometricWrappedKeyRepository.setWrappedKeyData(
                        BiometricWrappedKeyData(
                            wrappedKey = wrappedKey,
                            iv = biometricCipher.iv
                        )
                    )
                }
            }.onSuccess {
                session.startSession(keyToWrap)
            }.asUnitResult()
        }
}