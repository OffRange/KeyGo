package de.davis.keygo.feature.autofill.domain.usecase

import de.davis.keygo.core.feature.autofill.FakeAutofillServiceRepository
import de.davis.keygo.core.feature.autofill.FakeChromeAutofillRepository
import de.davis.keygo.feature.autofill.domain.model.AutofillActivationStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AutofillActivationStatusUseCaseTest {

    private val autofillServiceRepository = FakeAutofillServiceRepository()
    private val chromeAutofillRepository = FakeChromeAutofillRepository()

    private val activationStatus = AutofillActivationStatusUseCase(
        autofillServiceRepository = autofillServiceRepository,
        chromeAutofillRepository = chromeAutofillRepository,
    )

    @Test
    fun `a fresh device with Chrome reports nothing turned on yet`() = runTest {
        assertEquals(
            AutofillActivationStatus(
                systemAutofillEnabled = false,
                chromeAvailable = true,
                chromeAutofillEnabled = false,
            ),
            activationStatus(),
        )
    }

    @Test
    fun `reports KeyGo selected as the system autofill service`() = runTest {
        autofillServiceRepository.enabled = true

        assertEquals(true, activationStatus().systemAutofillEnabled)
    }

    @Test
    fun `reports Chrome handing autofill to KeyGo`() = runTest {
        chromeAutofillRepository.enabled = true

        val status = activationStatus()

        assertEquals(true, status.chromeAvailable)
        assertEquals(true, status.chromeAutofillEnabled)
    }

    @Test
    fun `a device without Chrome reports Chrome as neither available nor enabled`() = runTest {
        chromeAutofillRepository.available = false
        autofillServiceRepository.enabled = true

        assertEquals(
            AutofillActivationStatus(
                systemAutofillEnabled = true,
                chromeAvailable = false,
                chromeAutofillEnabled = false,
            ),
            activationStatus(),
        )
    }
}
