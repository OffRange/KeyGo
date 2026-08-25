package de.davis.keygo.core.item.data.local.entity

import androidx.room3.ColumnInfo

internal class KeyInformation(
    @ColumnInfo(name = "wrapped_key")
    val wrappedKey: ByteArray,
    @ColumnInfo(name = "key_nonce")
    val keyNonce: ByteArray,
)