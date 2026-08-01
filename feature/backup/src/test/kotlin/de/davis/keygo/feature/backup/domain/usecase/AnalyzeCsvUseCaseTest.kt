package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.backup.FakeBackupFileStore
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.CsvColumnAnalysis
import de.davis.keygo.feature.backup.domain.model.CsvColumnType
import de.davis.keygo.feature.backup.domain.model.ImportError
import de.davis.keygo.feature.backup.domain.model.MappingConfidence
import de.davis.keygo.rust.FakeCsvBackupManager
import de.davisalessandro.keygo.rust.BackupException
import de.davisalessandro.keygo.rust.ColumnMapping
import de.davisalessandro.keygo.rust.Confidence
import de.davisalessandro.keygo.rust.CsvAnalysis
import de.davisalessandro.keygo.rust.CsvColumn
import de.davisalessandro.keygo.rust.FieldConfidence
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AnalyzeCsvUseCaseTest {

    private val fileStore = FakeBackupFileStore()
    private val csv = FakeCsvBackupManager()
    private fun useCase() = AnalyzeCsvUseCase(fileStore, csv)
    private val uri = BackupDestinationUri("content://in.csv")

    @Test
    fun `maps analysis to domain columns`() = runTest {
        fileStore.contents = "name,pwd\nEmail,s3cr3t\n"
        csv.analyzeResult = CsvAnalysis(
            columns = listOf(
                CsvColumn(0u, "name", listOf("Email")),
                CsvColumn(1u, "pwd", listOf("s3cr3t")),
            ),
            suggested = ColumnMapping(0u, null, null, 1u, null, null),
            confidence = FieldConfidence(
                title = Confidence.HIGH,
                url = null,
                username = null,
                password = Confidence.LOW,
                notes = null,
                totp = null,
            ),
        )

        val result = useCase()(uri)

        val analysis = assertIs<Result.Success<*, *>>(result).success as CsvColumnAnalysis
        assertEquals(2, analysis.columns.size)
        assertEquals(CsvColumnType.Title, analysis.columns[0].suggestedType)
        assertEquals(CsvColumnType.Password, analysis.columns[1].suggestedType)
        assertEquals(MappingConfidence.Low, analysis.columns[1].confidence)
    }

    @Test
    fun `unreadable file fails with FileUnreadable`() = runTest {
        fileStore.readError = IllegalStateException("disk error")

        val result = useCase()(uri)

        assertEquals(ImportError.FileUnreadable, assertIs<Result.Failure<*, ImportError>>(result).error)
    }

    @Test
    fun `blank file fails with EmptyFile`() = runTest {
        fileStore.contents = "   "

        val result = useCase()(uri)

        assertEquals(ImportError.EmptyFile, assertIs<Result.Failure<*, ImportError>>(result).error)
    }

    @Test
    fun `analyze exception maps to ParseFailed`() = runTest {
        fileStore.contents = "name\nEmail\n"
        val cause = BackupException.Csv("bad csv")
        csv.analyzeException = cause

        val result = useCase()(uri)

        assertEquals(ImportError.ParseFailed(cause), assertIs<Result.Failure<*, ImportError>>(result).error)
    }
}
