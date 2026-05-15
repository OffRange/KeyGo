package de.davis.keygo.core.feature.autofill

import android.service.autofill.Dataset
import android.service.autofill.FillRequest
import de.davis.keygo.feature.autofill.presentation.AutofillDatasetProvider
import de.davis.keygo.feature.autofill.presentation.model.AutofillValue
import de.davis.keygo.feature.autofill.presentation.model.Form

/**
 * Records [getFillingDataset] calls. Returns a reflectively-created dummy [Dataset].
 */
internal class FakeAutofillDatasetProvider : AutofillDatasetProvider {
    val getFillingDatasetCalls = mutableListOf<List<AutofillValue>>()
    override suspend fun getAutofillDatasets(request: FillRequest, form: Form): List<Dataset> =
        emptyList()

    override fun getFillingDataset(values: List<AutofillValue>): Dataset {
        getFillingDatasetCalls += values.toList()
        val ctor = Dataset::class.java.getDeclaredConstructor()
        ctor.isAccessible = true
        return ctor.newInstance()
    }
}
