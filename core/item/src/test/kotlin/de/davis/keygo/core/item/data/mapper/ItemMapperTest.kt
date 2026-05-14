package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.data.local.pojo.LightweightItem
import de.davis.keygo.core.item.data.local.pojo.LightweightItemSearchResult
import de.davis.keygo.core.item.data.local.pojo.MovableItemPojo
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.Item
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordCredential
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import de.davis.keygo.core.item.data.local.entity.KeyInformation as EntityKeyInformation

class ItemMapperTest {

    private fun testLogin(
        name: String = "My password",
        note: String? = null,
        pinned: Boolean = false,
    ) = Login(
        id = newItemId(),
        name = name,
        username = null,
        domainInfos = emptySet(),
        passwordCredential = PasswordCredential( // TODO(#43-task3)
            secret = PasswordSecret(EncryptedPayload.EMPTY),
            score = PasswordScore.Strong,
        ),
        totp = null,
        note = note,
        pinned = pinned,
        vaultId = newVaultId(),
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
    )

    // Item.toData() → ItemEntity

    @Test
    fun `Login toData maps id and vaultId`() {
        val login = testLogin()
        val entity = (login as Item).toData()

        assertEquals(login.id, entity.id)
        assertEquals(login.vaultId, entity.vaultId)
    }

    @Test
    fun `Login toData maps name`() {
        val entity = (testLogin(name = "Work login") as Item).toData()
        assertEquals("Work login", entity.name)
    }

    @Test
    fun `Login toData maps null note`() {
        assertEquals(null, (testLogin(note = null) as Item).toData().note)
    }

    @Test
    fun `Login toData maps non-null note`() {
        assertEquals("My note", (testLogin(note = "My note") as Item).toData().note)
    }

    @Test
    fun `Login toData sets itemType to Login`() {
        assertEquals(VaultItemType.Login, (testLogin() as Item).toData().itemType)
    }

    @Test
    fun `Login toData maps pinned`() {
        assertTrue((testLogin(pinned = true) as Item).toData().pinned)
        assertFalse((testLogin(pinned = false) as Item).toData().pinned)
    }

    // LightweightItem.toDomain() → LiteItem.Concrete

    @Test
    fun `LightweightItem toDomain copies all fields`() {
        val id = newItemId()
        val lightweight = LightweightItem(
            id = id,
            name = "Test item",
            itemType = VaultItemType.Login,
            pinned = true,
        )

        val lite = lightweight.toDomain()

        assertEquals(id, lite.id)
        assertEquals("Test item", lite.name)
        assertEquals(VaultItemType.Login, lite.itemType)
        assertTrue(lite.pinned)
    }

    // LightweightItemSearchResult.toDomain() → LiteItemSearchResult

    @Test
    fun `LightweightItemSearchResult toDomain copies all fields`() {
        val id = newItemId()
        val pojo = LightweightItemSearchResult(
            id = id,
            name = "Search result",
            itemType = VaultItemType.Login,
            matchedName = true,
            matchedNote = false,
            pinned = false,
        )

        val result = pojo.toDomain()

        assertEquals(id, result.id)
        assertEquals("Search result", result.name)
        assertEquals(VaultItemType.Login, result.itemType)
        assertTrue(result.matchedName)
        assertFalse(result.matchedNote)
        assertFalse(result.pinned)
    }

    // MovableItemPojo.toDomain() → MovableItem

    @Test
    fun `MovableItemPojo toDomain maps id`() {
        val id = newItemId()
        val pojo = MovableItemPojo(
            id = id,
            keyInformation = EntityKeyInformation(byteArrayOf(), byteArrayOf()),
        )

        assertEquals(id, pojo.toDomain().id)
    }

    @Test
    fun `MovableItemPojo toDomain copies wrappedKey bytes`() {
        val pojo = MovableItemPojo(
            id = newItemId(),
            keyInformation = EntityKeyInformation(
                wrappedKey = byteArrayOf(10, 20, 30),
                keyNonce = byteArrayOf(),
            ),
        )

        assertContentEquals(byteArrayOf(10, 20, 30), pojo.toDomain().keyInformation.wrappedKey)
    }

    @Test
    fun `MovableItemPojo toDomain copies keyNonce bytes`() {
        val pojo = MovableItemPojo(
            id = newItemId(),
            keyInformation = EntityKeyInformation(
                wrappedKey = byteArrayOf(),
                keyNonce = byteArrayOf(7, 8, 9),
            ),
        )

        assertContentEquals(byteArrayOf(7, 8, 9), pojo.toDomain().keyInformation.keyNonce)
    }

    @Test
    fun `MovableItemPojo toDomain maps both key fields independently`() {
        val pojo = MovableItemPojo(
            id = newItemId(),
            keyInformation = EntityKeyInformation(
                wrappedKey = byteArrayOf(0xAA.toByte(), 0xBB.toByte()),
                keyNonce = byteArrayOf(0xCC.toByte(), 0xDD.toByte()),
            ),
        )

        val domain = pojo.toDomain()
        assertContentEquals(
            byteArrayOf(0xAA.toByte(), 0xBB.toByte()),
            domain.keyInformation.wrappedKey
        )
        assertContentEquals(
            byteArrayOf(0xCC.toByte(), 0xDD.toByte()),
            domain.keyInformation.keyNonce
        )
    }
}
