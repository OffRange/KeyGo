package de.davis.keygo.rust.wrap

import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.AccountRootKey
import de.davisalessandro.keygo.rust.KeyWrapException
import de.davisalessandro.keygo.rust.KeyWrapperInterface
import de.davisalessandro.keygo.rust.RootKek
import de.davisalessandro.keygo.rust.VaultKey
import de.davisalessandro.keygo.rust.WrappedKeyBlob
import java.util.UUID

typealias KeyWrapper = KeyWrapperInterface

fun KeyWrapper.unwrapAccountRootKeyWithResult(
    kek: RootKek,
    wrapped: WrappedKeyBlob,
    userId: UUID,
): Result<AccountRootKey, KeyWrapException> = runCatching {
    unwrapAccountRootKey(kek, wrapped, userId)
}.fold(
    onSuccess = { Result.Success(it) },
    onFailure = { Result.Failure(it as KeyWrapException) }
)

fun KeyWrapper.unwrapVaultKeyWithResult(
    ark: AccountRootKey,
    wrapped: WrappedKeyBlob,
    vaultId: UUID,
): Result<VaultKey, KeyWrapException> = runCatching {
    unwrapVaultKey(ark, wrapped, vaultId)
}.fold(
    onSuccess = { Result.Success(it) },
    onFailure = { Result.Failure(it as KeyWrapException) }
)

fun KeyWrapper.wrapAccountRootKeyWithResult(
    kek: RootKek,
    ark: AccountRootKey,
    userId: UUID,
): Result<WrappedKeyBlob, KeyWrapException> = runCatching {
    wrapAccountRootKey(kek, ark, userId)
}.fold(
    onSuccess = { Result.Success(it) },
    onFailure = { Result.Failure(it as KeyWrapException) }
)

fun KeyWrapper.wrapVaultKeyWithResult(
    ark: AccountRootKey,
    vaultKey: VaultKey,
    vaultId: UUID,
): Result<WrappedKeyBlob, KeyWrapException> = runCatching {
    wrapVaultKey(ark, vaultKey, vaultId)
}.fold(
    onSuccess = { Result.Success(it) },
    onFailure = { Result.Failure(it as KeyWrapException) }
)
