package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.data.local.pojo.LightweightItem
import de.davis.keygo.core.item.data.local.pojo.LightweightItemSearchResult
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.SecretData
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VaultItemMapperTest {

    private fun testPassword(
        name: String = "My password",
        note: String? = null,
        pinned: Boolean = false,
    ) = Password(
        id = newItemId(),
        name = name,
        username = null,
        domainInfos = emptySet(),
        score = Password.Score.Strong,
        password = SecretData.EMPTY_STRING,
        totpSecret = null,
        note = note,
        pinned = pinned,
        vaultId = newVaultId(),
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
    )

    // Item.toData() → ItemEntity

    @Test
    fun `Password toData maps id and vaultId`() {
        val password = testPassword()
        val entity = (password as Item).toData()

        assertEquals(password.id, entity.id)
        assertEquals(password.vaultId, entity.vaultId)
    }

    @Test
    fun `Password toData maps name`() {
        val entity = (testPassword(name = "Work login") as Item).toData()
        assertEquals("Work login", entity.name)
    }

    @Test
    fun `Password toData maps null note`() {
        assertEquals(null, (testPassword(note = null) as Item).toData().note)
    }

    @Test
    fun `Password toData maps non-null note`() {
        assertEquals("My note", (testPassword(note = "My note") as Item).toData().note)
    }

    @Test
    fun `Password toData sets itemType to Password`() {
        assertEquals(VaultItemType.Password, (testPassword() as Item).toData().itemType)
    }

    @Test
    fun `Password toData maps pinned`() {
        assertTrue((testPassword(pinned = true) as Item).toData().pinned)
        assertFalse((testPassword(pinned = false) as Item).toData().pinned)
    }

    // LightweightItem.toDomain() → LiteItem.Concrete

    @Test
    fun `LightweightItem toDomain copies all fields`() {
        val id = newItemId()
        val lightweight = LightweightItem(
            id = id,
            name = "Test item",
            itemType = VaultItemType.Password,
            pinned = true,
        )

        val lite = lightweight.toDomain()

        assertEquals(id, lite.id)
        assertEquals("Test item", lite.name)
        assertEquals(VaultItemType.Password, lite.itemType)
        assertTrue(lite.pinned)
    }

    // LightweightItemSearchResult.toDomain() → LiteItemSearchResult

    @Test
    fun `LightweightItemSearchResult toDomain copies all fields`() {
        val id = newItemId()
        val pojo = LightweightItemSearchResult(
            id = id,
            name = "Search result",
            itemType = VaultItemType.Password,
            matchedName = true,
            matchedNote = false,
            pinned = false,
        )

        val result = pojo.toDomain()

        assertEquals(id, result.id)
        assertEquals("Search result", result.name)
        assertEquals(VaultItemType.Password, result.itemType)
        assertTrue(result.matchedName)
        assertFalse(result.matchedNote)
        assertFalse(result.pinned)
    }
}
