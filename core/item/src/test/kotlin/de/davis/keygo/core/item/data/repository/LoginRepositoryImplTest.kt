package de.davis.keygo.core.item.data.repository

import androidx.room.withTransaction
import de.davis.keygo.core.item.data.local.dao.DomainInfoDao
import de.davis.keygo.core.item.data.local.dao.ItemDao
import de.davis.keygo.core.item.data.local.dao.LoginDao
import de.davis.keygo.core.item.data.local.dao.PasswordDao
import de.davis.keygo.core.item.data.local.dao.TotpDao
import de.davis.keygo.core.item.data.local.datasource.ItemDatabase
import de.davis.keygo.core.item.data.local.entity.credential.TotpEntity
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.core.item.domain.model.Totp
import de.davis.keygo.core.util.isFailure
import de.davis.keygo.core.util.isSuccess
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoginRepositoryImplTest {

    private val database = mockk<ItemDatabase>()
    private val itemDao = mockk<ItemDao>(relaxed = true)
    private val loginDao = mockk<LoginDao>(relaxed = true)
    private val passwordDao = mockk<PasswordDao>(relaxed = true)
    private val domainInfoDao = mockk<DomainInfoDao>(relaxed = true)
    private val totpDao = mockk<TotpDao>(relaxed = true)

    private val repository = LoginRepositoryImpl(
        database = database,
        itemDao = itemDao,
        loginDao = loginDao,
        passwordDao = passwordDao,
        domainInfoDao = domainInfoDao,
        totpDao = totpDao,
    )

    @BeforeTest
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @AfterTest
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `createOrUpdateLogin upserts totp row when login has a totp`() = runTest {
        val login = testLogin { id -> testTotp(loginId = id) }

        val result = repository.createOrUpdateLogin(login)

        assertTrue(result.isSuccess())
        coVerify(exactly = 1) { totpDao.upsert(any<TotpEntity>()) }
        coVerify(exactly = 0) { totpDao.delete(any()) }
    }

    @Test
    fun `createOrUpdateLogin deletes existing totp row when login has no totp`() = runTest {
        val login = testLogin(totpProvider = null)

        val result = repository.createOrUpdateLogin(login)

        assertTrue(result.isSuccess())
        coVerify(exactly = 1) { totpDao.delete(login.id) }
        coVerify(exactly = 0) { totpDao.upsert(any<TotpEntity>()) }
    }

    @Test
    fun `createOrUpdateLogin returns Success with login id`() = runTest {
        val login = testLogin(totpProvider = null)

        val result = repository.createOrUpdateLogin(login)

        assertTrue(result.isSuccess())
        assertEquals(login.id, result.success)
    }

    @Test
    fun `createOrUpdateLogin returns Failure when totp delete throws`() = runTest {
        val error = RuntimeException("db error")
        coEvery { totpDao.delete(any()) } throws error

        val result = repository.createOrUpdateLogin(testLogin(totpProvider = null))

        assertTrue(result.isFailure())
        assertEquals(error, result.error)
    }

    @Test
    fun `createOrUpdateLogin returns Failure when totp upsert throws`() = runTest {
        val error = RuntimeException("db error")
        coEvery { totpDao.upsert(any<TotpEntity>()) } throws error

        val result = repository.createOrUpdateLogin(testLogin { id -> testTotp(loginId = id) })

        assertTrue(result.isFailure())
        assertEquals(error, result.error)
    }

    private fun testLogin(totpProvider: ((ItemId) -> Totp)? = null): Login {
        val id = newItemId()
        return Login(
            id = id,
            username = "user",
            domainInfos = emptySet(),
            passwordScore = PasswordScore.Strong,
            password = PasswordSecret(EncryptedPayload.EMPTY),
            totp = totpProvider?.invoke(id),
            name = "Test",
            note = null,
            pinned = false,
            vaultId = newVaultId(),
            keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        )
    }

    private fun testTotp(loginId: ItemId) = Totp(
        loginId = loginId,
        secret = Totp.Secret(EncryptedPayload.EMPTY),
        accountName = "alice",
        issuer = "example",
    )
}
