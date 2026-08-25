package de.davis.keygo.core.item.data.repository

import de.davis.keygo.core.item.FakeTransactionRunner
import de.davis.keygo.core.item.data.local.dao.ItemDao
import de.davis.keygo.core.item.data.local.dao.TagDao
import de.davis.keygo.core.item.data.local.pojo.ItemTagProjection
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.model.Tag
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ItemRepositoryImplTest {

    private val itemDao = mockk<ItemDao>(relaxed = true)
    private val tagDao = mockk<TagDao>(relaxed = true)

    private val repository = ItemRepositoryImpl(
        transactionRunner = FakeTransactionRunner(),
        itemDao = itemDao,
        tagDao = tagDao,
    )

    @Test
    fun `deleteItems captures tag ids, deletes items, then prunes those tags`() = runTest {
        val id = newItemId()
        coEvery { tagDao.tagIdsForItem(id) } returns listOf(7L, 9L)

        repository.deleteItems(setOf(id))

        coVerifyOrder {
            tagDao.tagIdsForItem(id)
            itemDao.delete(setOf(id))
            tagDao.pruneOrphans(listOf(7L, 9L))
        }
    }

    @Test
    fun `deleteItems skips prune when items had no tags`() = runTest {
        val id = newItemId()
        coEvery { tagDao.tagIdsForItem(id) } returns emptyList()

        repository.deleteItems(setOf(id))

        coVerify(exactly = 0) { tagDao.pruneOrphans(any()) }
    }

    @Test
    fun `deleteItems removes every id in one dao call`() = runTest {
        val first = newItemId()
        val second = newItemId()
        coEvery { tagDao.tagIdsForItem(any()) } returns emptyList()

        repository.deleteItems(setOf(first, second))

        coVerify(exactly = 1) { itemDao.delete(setOf(first, second)) }
    }

    @Test
    fun `deleteItems prunes each tag once when items share a tag`() = runTest {
        val first = newItemId()
        val second = newItemId()
        coEvery { tagDao.tagIdsForItem(first) } returns listOf(7L)
        coEvery { tagDao.tagIdsForItem(second) } returns listOf(7L, 9L)

        repository.deleteItems(setOf(first, second))

        coVerify { tagDao.pruneOrphans(listOf(7L, 9L)) }
    }

    @Test
    fun `deleteItems touches nothing when the set is empty`() = runTest {
        repository.deleteItems(emptySet())

        coVerify(exactly = 0) { itemDao.delete(any<Collection<ItemId>>()) }
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
                a to setOf(Tag.of("Bank")!!, Tag.of("Work")!!),
                b to setOf(Tag.of("Bank")!!),
            ),
            result,
        )
    }
}
