package de.davis.keygo.core.item.data.local.dao

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import de.davis.keygo.core.item.data.local.datasource.ItemDatabase
import de.davis.keygo.core.item.data.local.entity.ItemEntity
import de.davis.keygo.core.item.data.local.entity.TagEntity
import de.davis.keygo.core.item.data.local.entity.Timestamp
import de.davis.keygo.core.item.data.local.entity.VaultEntity
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.Vault
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import de.davis.keygo.core.item.data.local.entity.KeyInformation as EntityKeyInformation

internal class ItemDaoSearchTest {

    private lateinit var db: ItemDatabase
    private lateinit var itemDao: ItemDao

    private val vaultId: VaultId = newVaultId()

    @BeforeTest
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(mockk(relaxed = true), ItemDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        itemDao = db.itemDao()
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
    fun tearDown() = db.close()

    private suspend fun insertItem(
        name: String,
        note: String? = null,
        tags: Set<String> = emptySet(),
    ): ItemId {
        val id = newItemId()
        itemDao.upsert(
            ItemEntity(
                id = id,
                vaultId = vaultId,
                name = name,
                note = note,
                itemType = VaultItemType.Login,
                pinned = false,
                keyInformation = EntityKeyInformation(byteArrayOf(), byteArrayOf()),
                timestamp = Timestamp(createdAt = 0L, modifiedAt = null),
            )
        )
        if (tags.isNotEmpty())
            db.tagDao().syncTags(
                id,
                tags.map { TagEntity(value = it, normalized = it.lowercase()) }.toSet(),
            )
        return id
    }

    @Test
    fun `searchItem matches an item by tag value only`() = runTest {
        val id = insertItem(name = "Chase", note = null, tags = setOf("Banking"))

        val results = itemDao.searchItem(query = "Bank", normalizedQuery = "bank")

        assertEquals(1, results.size)
        val r = results.single()
        assertEquals(id, r.id)
        assertFalse(r.matchedName)
        assertFalse(r.matchedNote)
        assertTrue(r.matchedTag)
    }

    @Test
    fun `searchItem still matches by name and sets matchedName`() = runTest {
        insertItem(name = "Bank of Earth")

        val r = itemDao.searchItem(query = "Bank", normalizedQuery = "bank").single()

        assertTrue(r.matchedName)
        assertFalse(r.matchedTag)
    }

    @Test
    fun `searchItem does not return items with no name note or tag match`() = runTest {
        insertItem(name = "Email", note = "personal", tags = setOf("Mail"))

        assertTrue(itemDao.searchItem(query = "Bank", normalizedQuery = "bank").isEmpty())
    }

    @Test
    fun `searchItem tag match still passes an explicit itemType filter`() = runTest {
        insertItem(name = "Chase", tags = setOf("Bank"))

        assertEquals(
            1,
            itemDao.searchItem(
                query = "Bank",
                normalizedQuery = "bank",
                itemType = VaultItemType.Login,
            ).size,
        )
    }
}
