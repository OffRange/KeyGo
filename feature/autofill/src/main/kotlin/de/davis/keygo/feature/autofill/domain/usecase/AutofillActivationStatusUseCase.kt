package de.davis.keygo.feature.autofill.domain.usecase

import de.davis.keygo.feature.autofill.domain.model.AutofillActivationStatus
import de.davis.keygo.feature.autofill.domain.model.ChromeAutofillState
import de.davis.keygo.feature.autofill.domain.repository.AutofillServiceRepository
import de.davis.keygo.feature.autofill.domain.repository.ChromeAutofillRepository
import org.koin.core.annotation.Single

@Single
class AutofillActivationStatusUseCase(
    private val autofillServiceRepository: AutofillServiceRepository,
    private val chromeAutofillRepository: ChromeAutofillRepository,
) {

    suspend operator fun invoke(): AutofillActivationStatus {
        val chrome = chromeAutofillRepository.autofillState()

        return AutofillActivationStatus(
            systemAutofillEnabled = autofillServiceRepository.isEnabled(),
            chromeAvailable = chrome != ChromeAutofillState.Unavailable,
            chromeAutofillEnabled = chrome == ChromeAutofillState.Enabled,
        )
    }
}
