package de.davis.passwordmanager.services.autofill.builder

import android.service.autofill.Dataset
import de.davis.passwordmanager.database.SecureElementManager
import de.davis.passwordmanager.database.entities.details.password.PasswordDetails
import de.davis.passwordmanager.services.autofill.entities.AutofillForm
import de.davis.passwordmanager.utils.BrowserUtil

object SuggestedDatasetBuilder {

    suspend fun AutofillForm.getNSuggestions(
        n: Int,
        typeId: Int,
        builder: (TextProvider, requestCode: Int) -> Dataset
    ): List<Dataset> = url?.let { url ->
        SecureElementManager.getSecureElements(typeId)
            .take(n)
            .filter {
                (it.detail as PasswordDetails).origin.couldBeUrl(url) ||
                        it.title.couldBeUrl(url)
            }
            .mapIndexed { index, element -> builder(element.getTextProvider(), index) }
    } ?: emptyList()


    private fun String.couldBeUrl(actualUrl: String): Boolean =
        BrowserUtil.couldBeUrl(this, actualUrl)
}