package de.davis.passwordmanager.services.autofill

import android.app.assist.AssistStructure.ViewNode
import android.os.Build
import android.service.autofill.FillRequest
import android.text.InputType
import android.view.View
import android.widget.EditText
import androidx.annotation.RequiresApi
import de.davis.passwordmanager.services.autofill.entities.AutofillField
import de.davis.passwordmanager.services.autofill.entities.AutofillForm
import de.davis.passwordmanager.services.autofill.entities.UserCredentialsType
import java.text.Normalizer

@RequiresApi(Build.VERSION_CODES.O)
class NodeTraverse(private val requestFlags: Int = 0) {

    var autofillForm: AutofillForm = AutofillForm()
        private set

    var url: String? = null
        private set

    private fun ViewNode.getUrl(): String? {
        val domain = webDomain ?: return null
        if (domain.isBlank()) return null
        val scheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            webScheme ?: "https"
        } else {
            "https"
        }

        return "$scheme://$domain"
    }

    fun traverseNode(node: ViewNode) {
        if (url == null) {
            url = node.getUrl()
        }

        if (node.childCount > 0) {
            (0 until node.childCount).map { node.getChildAt(it) }.forEach {
                traverseNode(it)
            }

            return
        }

        // Node does not contain any children -> this is a potential node to add

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            when (node.importantForAutofill) {
                View.IMPORTANT_FOR_AUTOFILL_AUTO,
                View.IMPORTANT_FOR_AUTOFILL_YES,
                View.IMPORTANT_FOR_AUTOFILL_YES_EXCLUDE_DESCENDANTS -> {
                }

                else -> {
                    if ((requestFlags and FillRequest.FLAG_MANUAL_REQUEST) != FillRequest.FLAG_MANUAL_REQUEST)
                        return
                }
            }
        }

        if (node.autofillId == null)
            return

        if (!node.isInput())
            return

        when (val fieldType = findFieldType(node)) {
            UserCredentialsType.Unidentified -> {}
            else -> {
                autofillForm.autofillFields += AutofillField(
                    node.autofillId!! /*We made sure that we don't reach this code if it is null*/,
                    node.isFocused,
                    fieldType
                )
            }
        }


    }


    private fun findFieldType(node: ViewNode): UserCredentialsType {
        // Try getting type by auto fill hints
        when (val field = findFieldTypeByNodesAutofillHints(node)) {
            UserCredentialsType.Unidentified -> {}
            else -> return field
        }
        // No suitable autofill hint found

        // Try getting type by HTML-Info
        when (val fieldType = findFieldTypeByHTML(node)) {
            UserCredentialsType.Unidentified -> {}
            else -> return fieldType
        }

        // Try getting type by input type
        when (val fieldType = findFieldTypeByInputType(node)) {
            UserCredentialsType.Unidentified -> {}
            else -> return fieldType
        }

        // Try getting type by text, hint, idEntry if no field type is found yet
        when (val fieldType = findFieldTypeFromPropertiesOfNode(node)) {
            UserCredentialsType.Unidentified -> {}
            else -> return fieldType
        }

        // TODO Try to understand the form/context

        return UserCredentialsType.Unidentified
    }

    private fun findFieldTypeFromPropertiesOfNode(node: ViewNode): UserCredentialsType {
        node.run {
            buildList {
                add(text)
                add(hint)
                add(idEntry)
                addAll(autofillOptions.orEmpty())
            }
        }.filterNotNull()
            .map(CharSequence::toString)
            .map { it.prepAutofillHint() }
            .forEach {
                when (val fieldType = findFieldTypeByAutofillHint(it)) {
                    UserCredentialsType.Unidentified -> {}
                    else -> return fieldType
                }
            }

        return UserCredentialsType.Unidentified
    }

    private fun findFieldTypeByNodesAutofillHints(node: ViewNode): UserCredentialsType {
        for (hint in node.autofillHints.orEmpty()) {
            return findFieldTypeByAutofillHint(hint)
        }

        return UserCredentialsType.Unidentified
    }

    private fun findFieldTypeByAutofillHint(autofillHint: String): UserCredentialsType {
        val userCredentialsType = when (autofillHint) {
            View.AUTOFILL_HINT_USERNAME,
            View.AUTOFILL_HINT_EMAIL_ADDRESS -> UserCredentialsType.Identifier

            View.AUTOFILL_HINT_PASSWORD -> UserCredentialsType.Password

            else -> UserCredentialsType.Unidentified
        }

        if (userCredentialsType != UserCredentialsType.Unidentified)
            return userCredentialsType

        if (USERNAME_REGEX.containsMatchIn(autofillHint)) return UserCredentialsType.Identifier

        return UserCredentialsType.Unidentified
    }


    private fun findFieldTypeByHTML(node: ViewNode): UserCredentialsType {
        val attr = node.htmlInfo?.attributes ?: return UserCredentialsType.Unidentified

        val type = attr.firstOrNull { it.first == "type" }
        when (type?.second) {
            "password" -> return UserCredentialsType.Password
            "email" -> return UserCredentialsType.Identifier

            //TODO implement other field detections
            else -> {}
        }


        return UserCredentialsType.Unidentified
    }

    private fun findFieldTypeByInputType(node: ViewNode) = node.run {
        when {
            this.hasInputType(
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
                InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
            ) -> UserCredentialsType.Identifier

            this.hasInputType(
                InputType.TYPE_CLASS_PHONE,
            ) -> UserCredentialsType.Identifier

            this.hasInputType(
                InputType.TYPE_TEXT_VARIATION_PERSON_NAME,
            ) -> UserCredentialsType.Identifier

            this.hasInputType(
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            ) -> UserCredentialsType.Password

            else -> UserCredentialsType.Unidentified
        }
    }

    private fun ViewNode.hasInputType(vararg types: Int): Boolean =
        types.any { inputType and InputType.TYPE_MASK_VARIATION == it }

    private fun ViewNode.isInput() = isEnabled && className == EditText::class.java.name


    private fun String.prepAutofillHint() =
        Normalizer.normalize(this, Normalizer.Form.NFD)
            .replace(Regex("\\p{Punct}"), "")

    companion object {
        val USERNAME_REGEX = Regex(
            "(username|login|user|email|account|" +
                    "usuario|correo|cuenta|nombredeusuario|" +
                    "nomd'utilisateur|compte|adressemail|" +
                    "benutzername|konto|emailadresse|" +
                    "nomeusuario|conta|" +
                    "nomeutente|" +
                    "imyapolzovatelya|uchetnayazapis|elektronnayapochta|" +
                    "yonghuming|zhanghu|dianziyoujian|" +
                    "yūzāmei|akaunto|mēruadoresu|" +
                    "ismalmustakhdim|alhisab|albaridal(')?iliktruni)",
            RegexOption.IGNORE_CASE
        )
    }
}