package de.davis.keygo.migration.create_access

import de.davis.keygo.migration.create_access.domain.model.MainPassword
import de.davis.keygo.migration.create_access.domain.repository.MainPasswordRepository
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
