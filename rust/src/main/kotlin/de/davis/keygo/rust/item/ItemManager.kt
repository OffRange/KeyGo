package de.davis.keygo.rust.item

import de.davis.keygo.core.util.Result
import de.davisalessandro.keygo.rust.EncryptedItemBlob
import de.davisalessandro.keygo.rust.ItemAad
import de.davisalessandro.keygo.rust.ItemCryptoException
import de.davisalessandro.keygo.rust.ItemKey
import de.davisalessandro.keygo.rust.ItemManagerInterface

typealias ItemManager = ItemManagerInterface

fun ItemManager.encryptItemDataWithResult(
    itemKey: ItemKey,
    data: ByteArray,
    aad: ItemAad
): Result<EncryptedItemBlob, ItemCryptoException> = runCatching {
    encryptItemData(itemKey = itemKey, data = data, aad = aad)
}.fold(
    onSuccess = { Result.Success(it) },
    onFailure = { Result.Failure(it as ItemCryptoException) }
)

fun ItemManager.decryptItemDataWithResult(
    itemKey: ItemKey,
    blob: EncryptedItemBlob,
    aad: ItemAad
): Result<ByteArray, ItemCryptoException> = runCatching {
    decryptItemData(itemKey = itemKey, aad = aad, blob = blob)
}.fold(
    onSuccess = { Result.Success(it) },
    onFailure = { Result.Failure(it as ItemCryptoException) }
)