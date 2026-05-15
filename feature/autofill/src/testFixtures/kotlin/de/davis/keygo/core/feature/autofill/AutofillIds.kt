package de.davis.keygo.core.feature.autofill

import android.view.autofill.AutofillId

/**
 * Builds an [AutofillId] for tests. The `int` constructor is hidden on the platform,
 * so it is reached reflectively (requires a Robolectric-backed runtime).
 */
fun autofillId(viewId: Int = 1): AutofillId {
    val ctor = AutofillId::class.java.getDeclaredConstructor(Int::class.java)
    ctor.isAccessible = true
    return ctor.newInstance(viewId)
}
