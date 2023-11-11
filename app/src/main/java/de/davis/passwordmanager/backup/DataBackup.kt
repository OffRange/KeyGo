package de.davis.passwordmanager.backup

import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.widget.Toast
import androidx.annotation.IntDef
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import de.davis.passwordmanager.R
import de.davis.passwordmanager.dialog.LoadingDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import javax.crypto.AEADBadTagException


const val TYPE_EXPORT = 0
const val TYPE_IMPORT = 1

@IntDef(TYPE_EXPORT, TYPE_IMPORT)
annotation class Type

abstract class DataBackup(val context: Context) {

    private lateinit var loadingDialog: LoadingDialog

    @Throws(Exception::class)
    internal abstract suspend fun runExport(outputStream: OutputStream): Result

    @Throws(Exception::class)
    internal abstract suspend fun runImport(inputStream: InputStream): Result

    open suspend fun execute(@Type type: Int, uri: Uri, onSyncedHandler: OnSyncedHandler? = null) {
        val resolver = context.contentResolver
        loadingDialog = LoadingDialog(context).apply {
            setTitle(if (type == TYPE_EXPORT) R.string.export else R.string.import_str)
            setMessage(R.string.wait_text)
        }
        val alertDialog = withContext(Dispatchers.Main) { loadingDialog.show() }

        try {
            withContext(Dispatchers.IO) {
                val result: Result = when (type) {
                    TYPE_EXPORT -> resolver.openOutputStream(uri)?.use { runExport(it) }!!

                    TYPE_IMPORT -> resolver.openInputStream(uri)?.use { runImport(it) }!!

                    else -> Result.Error("Unexpected error occurred")
                }

                handleResult(result, onSyncedHandler)
            }
        } catch (e: Exception) {
            if (e is NullPointerException) return
            error(e)
        } finally {
            alertDialog.dismiss()
        }
    }

    internal suspend fun error(exception: Exception) {
        val msg = if (exception is AEADBadTagException)
            context.getString(R.string.password_does_not_match)
        else
            exception.message

        withContext(Dispatchers.Main) {
            MaterialAlertDialogBuilder(context).apply {
                setTitle(R.string.error_title)
                setMessage(msg)
                setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int -> }
            }.show()
        }

        exception.printStackTrace()
    }

    private suspend fun handleResult(result: Result, onSyncedHandler: OnSyncedHandler?) =
        withContext(Dispatchers.Main) {
            if (result is Result.Success) {
                Toast.makeText(
                    context,
                    if (result.type == TYPE_EXPORT) R.string.backup_stored else R.string.backup_restored,
                    Toast.LENGTH_LONG
                ).show()
                handleSyncHandler(onSyncedHandler, result)
                return@withContext
            }

            MaterialAlertDialogBuilder(context).apply {
                setPositiveButton(R.string.ok) { _: DialogInterface?, _: Int ->
                    handleSyncHandler(
                        onSyncedHandler,
                        result
                    )
                }

                if (result is Result.Error) {
                    setTitle(R.string.error_title)
                    setMessage(result.message)
                } else if (result is Result.Duplicate) {
                    setTitle(R.string.warning)
                    setMessage(
                        context.resources.getQuantityString(
                            R.plurals.item_existed,
                            result.count,
                            result.count
                        )
                    )
                }
            }.show()
        }

    private fun handleSyncHandler(onSyncedHandler: OnSyncedHandler?, result: Result) {
        onSyncedHandler?.onSynced(result)
    }

    internal suspend fun notifyUpdate(current: Int, max: Int) {
        withContext(Dispatchers.Main) {
            loadingDialog.updateProgress(current, max)
        }
    }

    interface OnSyncedHandler {
        fun onSynced(result: Result?)
    }
}