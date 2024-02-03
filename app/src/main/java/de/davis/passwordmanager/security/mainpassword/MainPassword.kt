package de.davis.passwordmanager.security.mainpassword

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.time.Instant

fun UserMainPassword.toNormal() = MainPassword(
    hexHash, Instant.ofEpochSecond(
        createdAt.seconds,
        createdAt.nanos.toLong()
    )
)

@Parcelize
data class MainPassword(val hexHash: String, val createdAt: Instant) : Parcelable

val MainPassword.isEmpty: Boolean
    get() = hexHash.isBlank()