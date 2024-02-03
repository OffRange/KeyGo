package de.davis.passwordmanager.backup.impl

import android.content.Context
import android.content.DialogInterface
import android.os.IBinder
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.textfield.TextInputLayout
import de.davis.passwordmanager.R
import de.davis.passwordmanager.backup.BackupOperation
import de.davis.passwordmanager.backup.PasswordProvider
import de.davis.passwordmanager.dialog.EditDialogBuilder
import de.davis.passwordmanager.ui.views.InformationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AndroidPasswordProvider(private val context: Context) : PasswordProvider {
    override suspend fun invoke(
        backupOperation: BackupOperation,
        callback: suspend (password: String) -> Unit
    ) {
        withContext(Dispatchers.Main) {
            val information = InformationView.Information().apply {
                hint = context.getString(R.string.password)
                inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD
                isSecret = true
            }

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
                    hideKeyboardFrom(
                        context,
                        alertDialog.window?.decorView?.windowToken
                    )
                    alertDialog.dismiss()
                    CoroutineScope(Job() + Dispatchers.IO).launch {
                        callback(password)
                    }
                }
                withInformation(information)
                withStartIcon(
                    AppCompatResources.getDrawable(
                        context,
                        R.drawable.ic_baseline_password_24
                    )
                )
                setCancelable(backupOperation == BackupOperation.IMPORT)
            }.show()
        }
    }

    private fun hideKeyboardFrom(context: Context, windowToken: IBinder?) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowToken, 0)
    }
}