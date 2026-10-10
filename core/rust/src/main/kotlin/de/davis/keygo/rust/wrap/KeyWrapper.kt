package de.davis.keygo.rust.wrap

import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.ItemAad
import de.davisalessandro.keygo.rust.ItemKey
import de.davisalessandro.keygo.rust.KeyWrapException
import de.davisalessandro.keygo.rust.KeyWrapperInterface
import de.davisalessandro.keygo.rust.VaultKey
import de.davisalessandro.keygo.rust.WrappedKeyBlob

typealias KeyWrapper = KeyWrapperInterface

fun KeyWrapper.wrapItemKeyWithResult(
    vaultKey: VaultKey,
    itemKey: ItemKey,
    aad: ItemAad,
): Result<WrappedKeyBlob, KeyWrapException> = runCatching {
    wrapItemKey(vaultKey, itemKey, aad)
}.fold(
    onSuccess = { Result.Success(it) },
    onFailure = { Result.Failure(it as KeyWrapException) }
)

fun KeyWrapper.unwrapItemKeyWithResult(
    vaultKey: VaultKey,
    wrapped: WrappedKeyBlob,
    aad: ItemAad,
): Result<ItemKey, KeyWrapException> = runCatching {
    unwrapItemKey(vaultKey, wrapped, aad)
}.fold(
    onSuccess = { Result.Success(it) },
    onFailure = { Result.Failure(it as KeyWrapException) }
)
