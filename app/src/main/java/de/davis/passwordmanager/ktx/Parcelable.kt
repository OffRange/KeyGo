package de.davis.passwordmanager.ktx

import android.os.Bundle
import androidx.core.os.BundleCompat

fun <A> Bundle.getParcelableCompat(key: String, clazz: Class<A>): A? {
    classLoader = clazz.classLoader

    // Due to issues with the new getParcelable API in Android API 33, BundleCompat.getParcelable
    // is utilized here to ensure stable behavior across different Android versions. This workaround
    // selectively applies the new getParcelable implementation for API levels 34 and above,
    // addressing the reported bug.
    // For more details, refer to the Google Issue Tracker: https://issuetracker.google.com/issues/274185314
    // and the related discussion on Stack Overflow: https://stackoverflow.com/questions/76067109/getparcelable-crashes-due-to-null-iftable-in-agp-8
    return BundleCompat.getParcelable(this, key, clazz)
}