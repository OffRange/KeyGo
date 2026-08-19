package de.davis.keygo.legacy_migration

import de.davis.keygo.legacy_migration.domain.model.MainPassword
import de.davis.keygo.legacy_migration.domain.repository.MainPasswordRepository
import java.time.Instant

/**
 * A fake wrapper for MainPasswordRepository. We do this via a wrapper as this allows us to expose
 * the internal [MainPassword] publically for tests
 */
class FakeMainPasswordRepository(var hash: String = "") {

    internal fun asMainPasswordRepository(): MainPasswordRepository =
        object : MainPasswordRepository {
            override suspend fun getMainPassword() = MainPassword(hash, Instant.EPOCH)
            override suspend fun clearMainPassword() {
                hash = ""
            }
        }
}
