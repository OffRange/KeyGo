@file:Suppress("DEPRECATION")

package de.davis.passwordmanager.ktx

import android.os.Build
import android.os.Bundle
import android.os.Parcelable

fun <A : Parcelable> Bundle.getParcelableCompat(key: String, clazz: Class<A>): A? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        classLoader = clazz.classLoader
        getParcelable(key, clazz)
    } else
        getParcelable(key) as? A
}