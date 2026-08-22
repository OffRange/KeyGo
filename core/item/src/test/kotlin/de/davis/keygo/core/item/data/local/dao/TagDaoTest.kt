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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import de.davis.keygo.core.item.data.local.entity.KeyInformation as EntityKeyInformation

internal class TagDaoTest {

    private lateinit var db: ItemDatabase
    private lateinit var tagDao: TagDao

    private val vaultId: VaultId = newVaultId()

    @BeforeTest
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(mockk(relaxed = true), ItemDatabase::class.java)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()
        tagDao = db.tagDao()
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

    private suspend fun insertItem(id: ItemId = newItemId()): ItemId {
        db.itemDao().upsert(
            ItemEntity(
                id = id,
                vaultId = vaultId,
                name = "Item",
                note = null,
                itemType = VaultItemType.Login,
                pinned = false,
                keyInformation = EntityKeyInformation(byteArrayOf(), byteArrayOf()),
                timestamp = Timestamp(createdAt = 0L, modifiedAt = null),
            )
        )
        return id
    }

    private fun tag(value: String) = TagEntity(value = value, normalized = value.lowercase())

    @Test
    fun `same value is stored once across items`() = runTest {
        val a = insertItem()
        val b = insertItem()

        tagDao.syncTags(a, setOf(tag("Work")))
        tagDao.syncTags(b, setOf(tag("Work")))

        val id1 = tagDao.findIdByNormalized("work")
        assertEquals(2, tagDao.tagIdsForItem(a).size + tagDao.tagIdsForItem(b).size)
        assertEquals(id1, tagDao.tagIdsForItem(a).single())
        assertEquals(id1, tagDao.tagIdsForItem(b).single())
    }

    @Test
    fun `dedup is case-insensitive via normalized key`() = runTest {
        val item = insertItem()

        tagDao.syncTags(item, setOf(tag("MyTag")))
        val firstId = tagDao.tagIdsForItem(item).single()

        tagDao.syncTags(item, setOf(tag("mytag")))

        assertEquals(firstId, tagDao.tagIdsForItem(item).single())
    }

    @Test
    fun `deleting an item cascades its cross-refs`() = runTest {
        val item = insertItem()
        tagDao.syncTags(item, setOf(tag("Work")))

        db.itemDao().delete(setOf(item))

        assertEquals(emptyList(), tagDao.tagIdsForItem(item))
    }

    @Test
    fun `orphan tag pruned when no item references it, shared tag kept`() = runTest {
        val a = insertItem()
        val b = insertItem()
        tagDao.syncTags(a, setOf(tag("Solo"), tag("Shared")))
        tagDao.syncTags(b, setOf(tag("Shared")))

        tagDao.syncTags(a, emptySet())

        assertNull(tagDao.findIdByNormalized("solo"))
        assertEquals(tagDao.findIdByNormalized("shared"), tagDao.tagIdsForItem(b).single())
    }

    @Test
    fun `deleting an item prunes its now-orphan tags`() = runTest {
        val item = insertItem()
        tagDao.syncTags(item, setOf(tag("Temp")))
        val tagIds = tagDao.tagIdsForItem(item)

        db.itemDao().delete(setOf(item))
        tagDao.pruneOrphans(tagIds)

        assertNull(tagDao.findIdByNormalized("temp"))
    }

    @Test
    fun `observeItemIdsWithAnyTag returns ids of items having any of the values`() = runTest {
        val a = insertItem()
        val b = insertItem()
        val c = insertItem()
        tagDao.syncTags(a, setOf(tag("Bank")))
        tagDao.syncTags(b, setOf(tag("Work"), tag("Bank")))
        tagDao.syncTags(c, setOf(tag("Personal")))

        val ids = tagDao.observeItemIdsWithAnyTag(setOf("bank")).first().toSet()

        assertEquals(setOf(a, b), ids)
    }

    @Test
    fun `observeItemIdsWithAnyTag unions multiple values`() = runTest {
        val a = insertItem()
        val b = insertItem()
        val c = insertItem()
        tagDao.syncTags(a, setOf(tag("Bank")))
        tagDao.syncTags(b, setOf(tag("Work")))
        tagDao.syncTags(c, setOf(tag("Personal")))

        val ids = tagDao.observeItemIdsWithAnyTag(setOf("bank", "work")).first().toSet()

        assertEquals(setOf(a, b), ids)
    }

    @Test
    fun `observeItemIdsWithAnyTag returns empty when no tag matches`() = runTest {
        val a = insertItem()
        tagDao.syncTags(a, setOf(tag("Bank")))

        assertEquals(emptyList(), tagDao.observeItemIdsWithAnyTag(setOf("nope")).first())
    }

    @Test
    fun `observeItemIdsWithAnyTag matches the stored normalized form regardless of display casing`() =
        runTest {
            val a = insertItem()
            tagDao.syncTags(a, setOf(tag("Bank")))

            // Caller is required to pass normalized (lower-cased, trimmed) values.
            assertEquals(listOf(a), tagDao.observeItemIdsWithAnyTag(setOf("bank")).first())
            assertEquals(emptyList(), tagDao.observeItemIdsWithAnyTag(setOf("Bank")).first())
        }

    @Test
    fun `observeItemTags emits one row per item-tag pair using display value`() = runTest {
        val a = insertItem()
        val b = insertItem()
        tagDao.syncTags(a, setOf(tag("Bank"), tag("Work")))
        tagDao.syncTags(b, setOf(tag("Bank")))

        val pairs = tagDao.observeItemTags().first().map { it.itemId to it.value }.toSet()

        assertEquals(
            setOf(
                a to "Bank",
                a to "Work",
                b to "Bank",
            ),
            pairs,
        )
    }

    @Test
    fun `observeItemTags omits items that have no tags`() = runTest {
        val a = insertItem()
        insertItem() // untagged item must not appear

        tagDao.syncTags(a, setOf(tag("Solo")))

        val pairs = tagDao.observeItemTags().first().map { it.itemId to it.value }

        assertEquals(listOf(a to "Solo"), pairs)
    }
}
