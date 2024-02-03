package de.davis.passwordmanager.backup.impl

import android.content.Context
import android.content.DialogInterface
import android.widget.Toast
import app.keemobile.kotpass.errors.CryptoError
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.davis.passwordmanager.R
import de.davis.passwordmanager.backup.BackupOperation
import de.davis.passwordmanager.backup.BackupResult
import de.davis.passwordmanager.backup.listener.BackupListener
import de.davis.passwordmanager.dialog.LoadingDialog

open class AndroidBackupListener(private val context: Context) : BackupListener {

    private lateinit var loadingDialogBuilder: LoadingDialog

    override fun initiateProgress(maxCount: Int) {
        loadingDialogBuilder.setMax(maxCount)
    }

    override fun onProgressUpdated(progress: Int) {
        loadingDialogBuilder.updateProgress(progress)
    }

    override fun onStart(backupOperation: BackupOperation) {
        loadingDialogBuilder = LoadingDialog(context).apply {
            setTitle(if (backupOperation == BackupOperation.EXPORT) R.string.export else R.string.import_str)
            setMessage(R.string.wait_text)
        }.also { it.show() }
    }

    override fun onSuccess(backupOperation: BackupOperation, backupResult: BackupResult) {
        loadingDialogBuilder.dismiss()
        when (backupResult) {
            is BackupResult.SuccessWithDuplicates -> {
                MaterialAlertDialogBuilder(context).apply {
                    setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int -> }

                    setTitle(R.string.warning)
                    setMessage(
                        context.resources.getQuantityString(
                            R.plurals.item_existed,
                            backupResult.duplicates,
                            backupResult.duplicates
                        )
                    )
                }.show()
            }

            is BackupResult.Success -> {
                Toast.makeText(context, R.string.backup_restored, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onFailure(backupOperation: BackupOperation, throwable: Throwable) {
        loadingDialogBuilder.dismiss()

        val msg = when (throwable) {
            is CryptoError.InvalidKey -> context.getString(R.string.password_does_not_match)
            else -> {
                when (throwable.message?.trim()) {
                    MSG_ROW_NUMBER_ERROR -> {
                        context.getString(R.string.csv_row_number_error)
                    }

                    else -> throwable.message
                }
            }
        }

        MaterialAlertDialogBuilder(context).apply {
            setTitle(R.string.error_title)
            setMessage(msg)
            setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int -> }
        }.show()

        throwable.printStackTrace()
    }

    companion object {
        const val MSG_ROW_NUMBER_ERROR = "error_row_number"
    }
}