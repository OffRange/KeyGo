package de.davis.keygo.feature.autofill.data.repository

import android.view.autofill.AutofillManager
import de.davis.keygo.feature.autofill.domain.repository.AutofillServiceRepository
import org.koin.core.annotation.Single

@Single
internal class AutofillServiceRepositoryImpl(
    private val autofillManager: AutofillManager,
) : AutofillServiceRepository {

    override fun isEnabled(): Boolean = autofillManager.hasEnabledAutofillServices()

    override fun disable() = autofillManager.disableAutofillServices()
}