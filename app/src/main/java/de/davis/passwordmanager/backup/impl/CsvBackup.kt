package de.davis.passwordmanager.backup.impl

import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVWriterBuilder
import com.opencsv.validators.RowFunctionValidator
import de.davis.passwordmanager.backup.BackupResult
import de.davis.passwordmanager.backup.DataBackup
import de.davis.passwordmanager.backup.ProgressContext
import de.davis.passwordmanager.backup.listener.BackupListener
import de.davis.passwordmanager.database.ElementType
import de.davis.passwordmanager.database.SecureElementManager
import de.davis.passwordmanager.database.dtos.SecureElement
import de.davis.passwordmanager.database.entities.details.password.PasswordDetails
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter

class CsvBackup(backupListener: BackupListener = BackupListener.Empty) :
    DataBackup(backupListener = backupListener) {

    override suspend fun ProgressContext.runImport(inputStream: InputStream): BackupResult {
        val csvReader = CSVReaderBuilder(InputStreamReader(inputStream)).apply {
            withSkipLines(1)
            withRowValidator(
                RowFunctionValidator(
                    { s: Array<String?> -> s.size == 5 },
                    AndroidBackupListener.MSG_ROW_NUMBER_ERROR
                )
            )
        }.build()

        var line: Array<String> = emptyArray()
        val elements: List<SecureElement> =
            SecureElementManager.getSecureElements(ElementType.PASSWORD.typeId)

        var existed = 0
        csvReader.use {
            while (csvReader.readNext()?.also { line = it } != null) {
                // Skip lines where either the name (index 0) or password (index 3) is empty.
                // Assumes length validation by RowValidator, thus preventing IndexOutOfBoundsException.
                if (line[0].isEmpty() || line[3].isEmpty())
                    continue

                val title = line[0]
                val origin = line[1]
                val username = line[2]
                val pwd = line[3]

                if (elements.any { e -> e.title == title && (e.detail as PasswordDetails).let { it.password == pwd && it.username == username && it.origin == origin } }) {
                    existed++
                    continue
                }
                val details =
                    PasswordDetails(
                        pwd,
                        origin,
                        username
                    )
                SecureElementManager.insertElement(SecureElement(title, details))
            }
        }
        return if (existed != 0) BackupResult.SuccessWithDuplicates(existed) else BackupResult.Success()
    }

    override suspend fun ProgressContext.runExport(outputStream: OutputStream): BackupResult {
        val csvWriter = CSVWriterBuilder(OutputStreamWriter(outputStream)).build()

        val elements: List<SecureElement> =
            SecureElementManager.getSecureElements(ElementType.PASSWORD.typeId)

        csvWriter.use {
            it.writeNext(arrayOf("name", "url", "username", "password", "note"))
            it.writeAll(elements.map { pwd: SecureElement ->
                arrayOf(
                    pwd.title,
                    (pwd.detail as PasswordDetails).origin,
                    (pwd.detail as PasswordDetails).username,
                    (pwd.detail as PasswordDetails).password,
                    null
                )
            })
            it.flush()
        }
        return BackupResult.Success()
    }
}