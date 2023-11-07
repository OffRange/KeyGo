package de.davis.passwordmanager.security.mainpassword

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.Instant

@Parcelize
data class MainPassword(val hexHash: String, val createdAt: Instant = Instant.now()) : Parcelable {

    @OptIn(ExperimentalStdlibApi::class)
    constructor(hash: ByteArray) : this(hash.toHexString())

    companion object {

        @JvmField
        val EMPTY: MainPassword = MainPassword(ByteArray(0))
    }
}