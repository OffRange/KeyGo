@file:RequiresApi(Build.VERSION_CODES.O)

package de.davis.passwordmanager.services.autofill

import android.content.pm.PackageManager
import android.os.Build
import android.service.autofill.SaveRequest
import androidx.annotation.RequiresApi
import de.davis.passwordmanager.database.dtos.SecureElement
import de.davis.passwordmanager.database.entities.details.password.PasswordDetails
import de.davis.passwordmanager.services.autofill.entities.SaveField
import de.davis.passwordmanager.services.autofill.entities.SaveForm
import de.davis.passwordmanager.services.autofill.extensions.findChildById
import de.davis.passwordmanager.services.autofill.extensions.getWindowNodes
import java.net.URI

fun SaveForm.getElement(request: SaveRequest, packageManager: PackageManager): SecureElement {
    val origin: String = url ?: ""
    val username = identifierSaveField?.findNode(request)?.autofillValue?.textValue ?: ""
    val password = passwordSaveField?.findNode(request)?.autofillValue?.textValue ?: ""
    val packageName = request.fillContexts.firstOrNull()?.structure?.activityComponent?.packageName

    val title = packageName.orEmpty().let {
        URI(origin).host ?: packageManager.run {
            runCatching {
                getApplicationLabel(getApplicationInfo(it, 0)).toString()
            }.getOrDefault(it)
        }
    }

    return SecureElement(title, PasswordDetails(password.toString(), origin, username.toString()))
}

private fun SaveField.findNode(request: SaveRequest) = request.fillContexts.firstOrNull {
    it.requestId == requestId
}?.let { fillContext ->
    fillContext.getWindowNodes().firstNotNullOfOrNull {
        it.rootViewNode.findChildById(autofillId)
    }
}