package de.davis.keygo.core.item.data.local.dao

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.davis.keygo.core.item.data.local.datasource.ItemDatabase
import de.davis.keygo.core.item.data.local.entity.ItemEntity
import de.davis.keygo.core.item.data.local.entity.LoginEntity
import de.davis.keygo.core.item.data.local.entity.Timestamp
import de.davis.keygo.core.item.data.local.entity.VaultEntity
import de.davis.keygo.core.item.data.local.entity.credential.PasskeyEntity
import de.davis.keygo.core.item.data.local.entity.credential.PasswordEntity
import de.davis.keygo.core.item.data.local.entity.credential.TotpEntity
import de.davis.keygo.core.item.data.local.pojo.LoginCredentialsProjection
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import de.davis.keygo.core.item.data.local.entity.KeyInformation as EntityKeyInformation

internal class LoginDaoTest {

    private lateinit var db: ItemDatabase
    private lateinit var loginDao: LoginDao

    private val vaultId: VaultId = newVaultId()
    private val payload = EncryptedPayload(byteArrayOf(1), byteArrayOf(2))

    @BeforeTest
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(mockk(relaxed = true), ItemDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        loginDao = db.loginDao()
        db.vaultDao().insert(
            VaultEntity(
                id = vaultId,
                name = "Vault",
                icon = Vault.Icon.Person,
                createdAt = 0L,
                keyInformation = EntityKeyInformation(byteArrayOf(), byteArrayOf()),
            )
        )
    }

    @AfterTest
    fun tearDown() {
        db.close()
    }

    private suspend fun insertLogin(id: ItemId = newItemId()): ItemId {
        db.itemDao().upsert(
            ItemEntity(
                id = id,
                vaultId = vaultId,
                name = "Login",
                note = null,
                itemType = VaultItemType.Login,
                pinned = false,
                keyInformation = EntityKeyInformation(byteArrayOf(), byteArrayOf()),
                timestamp = Timestamp(createdAt = 0L, modifiedAt = null),
            )
        )
        loginDao.upsert(LoginEntity(id = id, username = null))
        return id
    }

    private suspend fun addPassword(loginId: ItemId) = db.passwordDao().upsert(
        PasswordEntity(loginId = loginId, passwordScore = PasswordScore.Strong, password = payload)
    )

    private suspend fun addPasskey(loginId: ItemId, credentialId: Byte) =
        db.passkeyDao().insertPasskey(
            PasskeyEntity(
                credentialId = byteArrayOf(credentialId),
                loginId = loginId,
                rp = "example.com",
                privateKey = payload,
                name = "user",
                displayName = "User",
            )
        )

    private suspend fun addTotp(loginId: ItemId) = db.totpDao().upsert(
        TotpEntity(
            loginId = loginId,
            secret = payload,
            issuer = null,
            accountName = null,
            algorithm = "SHA1",
            digits = 6,
            period = 30,
        )
    )

    @Test
    fun `observeCredentials reports which credentials each login holds`() = runTest {
        val bare = insertLogin()
        val passwordOnly = insertLogin().also { addPassword(it) }
        val everything = insertLogin().also {
            addPassword(it)
            addPasskey(it, credentialId = 1)
            addPasskey(it, credentialId = 2)
            addTotp(it)
        }

        val rows = loginDao.observeCredentials().first().associateBy { it.id }

        assertEquals(
            mapOf(
                bare to LoginCredentialsProjection(bare, false, false, false),
                passwordOnly to LoginCredentialsProjection(passwordOnly, true, false, false),
                everything to LoginCredentialsProjection(everything, true, true, true),
            ),
            rows,
        )
    }

    @Test
    fun `observeCredentials drops a credential once its row is deleted`() = runTest {
        val login = insertLogin().also { addTotp(it) }
        assertEquals(true, loginDao.observeCredentials().first().single().hasTotp)

        db.totpDao().delete(login)

        assertEquals(false, loginDao.observeCredentials().first().single().hasTotp)
    }
}
