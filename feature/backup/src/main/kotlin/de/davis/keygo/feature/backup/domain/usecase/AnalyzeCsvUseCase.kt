package de.davis.keygo.feature.backup.domain.usecase

import de.davis.keygo.core.util.Result
import de.davis.keygo.core.util.resultBinding
import de.davis.keygo.feature.backup.domain.BackupFileStore
import de.davis.keygo.feature.backup.domain.mapper.toDomain
import de.davis.keygo.feature.backup.domain.mapper.toImportError
import de.davis.keygo.feature.backup.domain.model.BackupDestinationUri
import de.davis.keygo.feature.backup.domain.model.CsvColumnAnalysis
import de.davis.keygo.feature.backup.domain.model.ImportError
import de.davis.keygo.rust.backup.analyzeWithResult
import de.davisalessandro.keygo.rust.CsvBackupManagerInterface
import org.koin.core.annotation.Single

@Single
internal class AnalyzeCsvUseCase(
    private val fileStore: BackupFileStore,
    private val csvBackupManager: CsvBackupManagerInterface,
) {

    suspend operator fun invoke(uri: BackupDestinationUri): Result<CsvColumnAnalysis, ImportError> =
        resultBinding {
            val text = fileStore.read(uri).bind { ImportError.FileUnreadable }
            if (text.isBlank())
                Result.Failure<Nothing, ImportError>(ImportError.EmptyFile).bind()

            csvBackupManager.analyzeWithResult(text).bind { it.toImportError() }.toDomain()
        }
}
