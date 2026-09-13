package de.davis.keygo.feature.autofill.domain.usecase

import de.davis.keygo.feature.autofill.domain.model.AutofillActivationStatus
import de.davis.keygo.feature.autofill.domain.repository.AutofillServiceRepository
import de.davis.keygo.feature.autofill.domain.repository.ChromeAutofillRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.koin.core.annotation.Single

@Single
class AutofillActivationStatusUseCase(
    private val autofillServiceRepository: AutofillServiceRepository,
    private val chromeAutofillRepository: ChromeAutofillRepository,
) {

    suspend operator fun invoke(): AutofillActivationStatus = coroutineScope {
        val systemAutofillEnabled = async { autofillServiceRepository.isEnabled() }
        val chromeAvailable = async { chromeAutofillRepository.isAvailable() }
        val chromeAutofillEnabled = async { chromeAutofillRepository.isAutofillEnabled() }

        AutofillActivationStatus(
            systemAutofillEnabled = systemAutofillEnabled.await(),
            chromeAvailable = chromeAvailable.await(),
            chromeAutofillEnabled = chromeAvailable.await() && chromeAutofillEnabled.await(),
        )
    }
}