package de.davis.keygo.core.item.data.repository

import androidx.room.withTransaction
import de.davis.keygo.core.item.data.local.dao.ItemDao
import de.davis.keygo.core.item.data.local.dao.TagDao
import de.davis.keygo.core.item.data.local.datasource.ItemDatabase
import de.davis.keygo.core.item.data.local.pojo.ItemTagProjection
import de.davis.keygo.core.item.domain.alias.newItemId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ItemRepositoryImplTest {

    private val database = mockk<ItemDatabase>()
    private val itemDao = mockk<ItemDao>(relaxed = true)
    private val tagDao = mockk<TagDao>(relaxed = true)

    private val repository = ItemRepositoryImpl(
        database = database,
        itemDao = itemDao,
        tagDao = tagDao,
    )

    @BeforeTest
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @AfterTest
    fun tearDown() = unmockkStatic("androidx.room.RoomDatabaseKt")

    @Test
    fun `deleteItem captures tag ids, deletes item, then prunes those tags`() = runTest {
        val id = newItemId()
        coEvery { tagDao.tagIdsForItem(id) } returns listOf(7L, 9L)

        repository.deleteItem(id)

        coVerifyOrder {
            tagDao.tagIdsForItem(id)
            itemDao.delete(id)
            tagDao.pruneOrphans(listOf(7L, 9L))
        }
    }

    @Test
    fun `deleteItem skips prune when item had no tags`() = runTest {
        val id = newItemId()
        coEvery { tagDao.tagIdsForItem(id) } returns emptyList()

        repository.deleteItem(id)

        coVerify(exactly = 0) { tagDao.pruneOrphans(any()) }
    }

    @Test
    fun `observeTagsByItem groups rows into a tag set per item`() = runTest {
        val a = newItemId()
        val b = newItemId()
        every { tagDao.observeItemTags() } returns flowOf(
            listOf(
                ItemTagProjection(a, "Bank"),
                ItemTagProjection(a, "Work"),
                ItemTagProjection(b, "Bank"),
            )
        )

        val result = repository.observeTagsByItem().first()

        assertEquals(
            mapOf(
                a to setOf("Bank", "Work"),
                b to setOf("Bank"),
            ),
            result,
        )
    }
}
