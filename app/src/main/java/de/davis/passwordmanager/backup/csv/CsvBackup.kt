package de.davis.passwordmanager.backup.csv

import android.content.Context
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVWriterBuilder
import com.opencsv.validators.RowFunctionValidator
import de.davis.passwordmanager.R
import de.davis.passwordmanager.backup.DataBackup
import de.davis.passwordmanager.backup.Result
import de.davis.passwordmanager.backup.TYPE_EXPORT
import de.davis.passwordmanager.backup.TYPE_IMPORT
import de.davis.passwordmanager.database.ElementType
import de.davis.passwordmanager.database.SecureElementManager
import de.davis.passwordmanager.database.dtos.SecureElement
import de.davis.passwordmanager.database.entities.details.password.PasswordDetails
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter

class CsvBackup(context: Context) : DataBackup(context) {
    @Throws(Exception::class)
    override suspend fun runImport(inputStream: InputStream): Result {
        val csvReader = CSVReaderBuilder(InputStreamReader(inputStream)).apply {
            withSkipLines(1)
            withRowValidator(
                RowFunctionValidator(
                    { s: Array<String?> -> s.size == 5 },
                    context.getString(R.string.csv_row_number_error)
                )
            )
            withRowValidator(
                RowFunctionValidator(
                    { s: Array<String?> -> s.size == 5 },
                    context.getString(R.string.csv_row_number_error)
                )
            )
        }.build()

        var line: Array<String>
        val elements: List<SecureElement> =
            SecureElementManager.getSecureElements(ElementType.PASSWORD.typeId)

        var existed = 0
        csvReader.use {
            while (csvReader.readNext().also { line = it } != null) {
                if (line[0].isEmpty() || line[3].isEmpty()) // name and password must not be empty
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
        return if (existed != 0) Result.Duplicate(existed) else Result.Success(TYPE_IMPORT)
    }

    @Throws(Exception::class)
    override suspend fun runExport(outputStream: OutputStream): Result {
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
        return Result.Success(TYPE_EXPORT)
    }
}