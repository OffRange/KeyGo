package de.davis.keygo.feature.autofill.data.repository

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.view.autofill.AutofillManager
import de.davis.keygo.feature.autofill.domain.repository.AutofillServiceRepository
import org.koin.core.annotation.Single

@Single
internal class AutofillServiceRepositoryImpl(
    private val applicationContext: Context,
    private val autofillManager: AutofillManager,
) : AutofillServiceRepository {

    /**
     * Reads the selected autofill service straight from the OS setting rather than via
     * AutofillManager.hasEnabledAutofillServices(): the manager reports a stale `false` on the
     * first launch after a fresh install (the package's autofill state isn't warmed up until the
     * service is bound or re-selected), whereas the setting is authoritative immediately — it's
     * the same value the system Settings screen shows.
     *
     * AUTOFILL_SERVICE_SETTING is @hide in the SDK, but its key is stable and read the same way by
     * the system Settings UI.
     */
    override fun isEnabled(): Boolean {
        val selected =
            Settings.Secure.getString(applicationContext.contentResolver, AUTOFILL_SERVICE_SETTING)
        val component = selected?.let(ComponentName::unflattenFromString)
        return component?.packageName == applicationContext.packageName
    }

    override fun disable() = autofillManager.disableAutofillServices()

    private companion object {
        const val AUTOFILL_SERVICE_SETTING = "autofill_service"
    }
}
