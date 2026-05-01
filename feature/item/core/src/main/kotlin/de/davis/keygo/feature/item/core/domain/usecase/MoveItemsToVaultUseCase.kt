package de.davis.keygo.feature.item.core.domain.usecase

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.Password.Companion.LABEL_PASSWORD
import de.davis.keygo.core.item.domain.model.Password.Companion.LABEL_TOTP_SECRET
import de.davis.keygo.core.item.domain.repository.PasswordRepository
import de.davis.keygo.core.item.domain.repository.VaultRepository
import de.davis.keygo.core.security.domain.crypto.CryptographicScopeProvider
import de.davis.keygo.core.security.domain.crypto.decryptSecretData
import de.davis.keygo.core.security.domain.crypto.encryptSecretData
import de.davis.keygo.core.security.domain.crypto.model.WrappedItemKeyInformation
import de.davis.keygo.core.security.domain.crypto.model.WrappedVaultKeyInformation
import de.davis.keygo.core.security.domain.crypto.wrappedItemKeyInformation
import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.onFailure
import de.davis.keygo.feature.item.core.domain.model.MoveItemsError
import de.davisalessandro.keygo.rust.ItemAad
import org.koin.core.annotation.Single

@Single
class MoveItemsToVaultUseCase(
    private val cryptographicScopeProvider: CryptographicScopeProvider,
    private val passwordRepository: PasswordRepository,
    private val vaultRepository: VaultRepository,
) {

    suspend operator fun invoke(
        srcVaultId: VaultId,
        dstVaultId: VaultId,
    ): Result<Unit, MoveItemsError> {
        if (srcVaultId == dstVaultId) return Result.Success(Unit)

        val srcKey = vaultRepository.getKeyInformation(srcVaultId)
            ?: return Result.Failure(MoveItemsError.VaultNotFound(srcVaultId))
        val dstKey = vaultRepository.getKeyInformation(dstVaultId)
            ?: return Result.Failure(MoveItemsError.VaultNotFound(dstVaultId))

        passwordRepository.getPasswordsByVault(srcVaultId).forEach { password ->
            runCatching { rewrapAndPersist(password, srcKey, dstKey, dstVaultId) }
                .onFailure {
                    return Result.Failure(MoveItemsError.ItemMoveFailed(password.id, it))
                }
        }
        return Result.Success(Unit)
    }

    private suspend fun rewrapAndPersist(
        existing: Password,
        srcVaultKey: KeyInformation,
        dstVaultKey: KeyInformation,
        dstVaultId: VaultId,
    ) {
        val (passwordPlaintext, totpPlaintext) = cryptographicScopeProvider.itemScope(
            wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                wrappedVaultKey = srcVaultKey,
                vaultId = existing.vaultId,
            ),
            wrappedItemKeyInformation = existing.wrappedItemKeyInformation(),
        ) {
            existing.password.decryptSecretData(LABEL_PASSWORD) to
                    existing.totpSecret?.decryptSecretData(LABEL_TOTP_SECRET)
        }

        val moved = cryptographicScopeProvider.itemScope(
            wrappedVaultKeyInformation = WrappedVaultKeyInformation(
                wrappedVaultKey = dstVaultKey,
                vaultId = dstVaultId,
            ),
            wrappedItemKeyInformation = WrappedItemKeyInformation(
                itemAad = ItemAad(itemId = existing.id, vaultId = dstVaultId),
            ),
        ) {
            existing.copy(
                vaultId = dstVaultId,
                keyInformation = wrapCurrentItemKey(),
                password = passwordPlaintext.encryptSecretData(LABEL_PASSWORD),
                totpSecret = totpPlaintext?.encryptSecretData(LABEL_TOTP_SECRET),
            )
        }

        passwordRepository.createOrUpdatePassword(moved).onFailure { throw it }
    }
}
