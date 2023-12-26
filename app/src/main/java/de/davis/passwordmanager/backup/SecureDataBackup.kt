package de.davis.passwordmanager.backup

import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.textfield.TextInputLayout
import de.davis.passwordmanager.R
import de.davis.passwordmanager.dialog.EditDialogBuilder
import de.davis.passwordmanager.ui.views.InformationView.Information
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

abstract class SecureDataBackup(context: Context) : DataBackup(context) {

    lateinit var password: String

    private suspend fun requestPassword(
        @Type type: Int,
        uri: Uri,
        onSyncedHandler: OnSyncedHandler?
    ) {
        val information = Information().apply {
            hint = context.getString(R.string.password)
            inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSecret = true
        }
        withContext(Dispatchers.Main) {
            EditDialogBuilder(context).apply {
                setTitle(R.string.password)
                setPositiveButton(R.string.yes) { _: DialogInterface?, _: Int -> }
                setButtonListener(
                    DialogInterface.BUTTON_POSITIVE,
                    R.string.yes
                ) { dialog, _, password ->
                    /*
                    Needed for the error message that appears when the password (field) is empty.
                    otherwise the dialog would close itself
                    */

                    val alertDialog = dialog as AlertDialog
                    if (password.isEmpty()) {
                        alertDialog.findViewById<TextInputLayout>(R.id.textInputLayout)?.error =
                            context.getString(R.string.is_not_filled_in)
                        return@setButtonListener
                    }
                    alertDialog.dismiss()
                    this@SecureDataBackup.password = password
                    CoroutineScope(Job() + Dispatchers.IO).launch {
                        super.execute(type, uri, onSyncedHandler)
                    }
                }
                withInformation(information)
                withStartIcon(
                    AppCompatResources.getDrawable(
                        context,
                        R.drawable.ic_baseline_password_24
                    )
                )
                setCancelable(type == TYPE_IMPORT)
            }.show()
        }
    }

    override suspend fun execute(@Type type: Int, uri: Uri, onSyncedHandler: OnSyncedHandler?) {
        requestPassword(type, uri, onSyncedHandler)
    }
}