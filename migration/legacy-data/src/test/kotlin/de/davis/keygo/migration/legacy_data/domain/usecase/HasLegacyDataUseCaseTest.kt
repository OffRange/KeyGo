package de.davis.keygo.migration.legacy_data.domain.usecase

import de.davis.keygo.core.util.Result
import de.davis.keygo.migration.legacy_data.domain.model.LegacyReadFailure
import de.davis.keygo.migration.legacy_data.domain.repository.LegacyDatabaseState
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HasLegacyDataUseCaseTest {

    private val legacyRepository = FakeLegacyItemRepository()

    private fun useCase() = HasLegacyDataUseCase(legacyRepository)

    @Test
    fun `reports data waiting when the file is v1's and still holds rows`() = runTest {
        legacyRepository.state = LegacyDatabaseState.Present
        legacyRepository.countResult = Result.Success(2)

        assertTrue(useCase()())
    }

    @Test
    fun `reports nothing waiting when the file is v1's but empty`() = runTest {
        legacyRepository.state = LegacyDatabaseState.Present
        legacyRepository.countResult = Result.Success(0)

        assertFalse(useCase()())
    }

    @Test
    fun `reports nothing waiting when the rows cannot be counted`() = runTest {
        legacyRepository.state = LegacyDatabaseState.Present
        legacyRepository.countResult = Result.Failure(LegacyReadFailure.DatabaseUnreadable)

        assertFalse(useCase()())
    }

    @Test
    fun `reports nothing waiting on a clean install`() = runTest {
        legacyRepository.state = LegacyDatabaseState.Absent
        legacyRepository.countResult = Result.Success(5)

        assertFalse(useCase()())
    }

    @Test
    fun `reports nothing waiting for a file that is not v1's`() = runTest {
        legacyRepository.state = LegacyDatabaseState.NotLegacy
        legacyRepository.countResult = Result.Success(5)

        assertFalse(useCase()())
    }

    @Test
    fun `reports nothing waiting for a file that cannot be read`() = runTest {
        legacyRepository.state = LegacyDatabaseState.Unreadable
        legacyRepository.countResult = Result.Success(5)

        assertFalse(useCase()())
    }
}
