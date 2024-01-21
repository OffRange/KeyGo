package de.davis.passwordmanager.services.autofill.builder

import de.davis.passwordmanager.database.dtos.SecureElement
import de.davis.passwordmanager.database.entities.details.password.PasswordDetails

data class TextProvider(
    val title: String,
    val subtitle: String? = null,
    val element: SecureElement? = null
) {
    constructor(element: SecureElement, subtitleProvider: (SecureElement) -> String?) : this(
        element = element,
        title = element.title,
        subtitle = subtitleProvider(element)
    )
}

fun SecureElement.getTextProvider() =
    TextProvider(this) { (this.detail as PasswordDetails).username.ifBlank { "----" } }
