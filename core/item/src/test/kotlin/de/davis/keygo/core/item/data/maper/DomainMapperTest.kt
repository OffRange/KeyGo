package de.davis.keygo.core.item.data.maper

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Password
import de.davis.keygo.core.item.domain.model.SecretData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainMapperTest {

    private fun password(
        id: ItemId = newItemId(),
        domainInfos: Set<DomainInfo> = emptySet(),
    ) = Password(
        id = id,
        username = null,
        domainInfos = domainInfos,
        score = Password.Score.Strong,
        password = SecretData.EMPTY_STRING,
        totp = null,
        vaultId = newVaultId(),
        name = "Test",
        keyInformation = KeyInformation(wrappedKey = byteArrayOf(), keyNonce = byteArrayOf()),
        note = null,
        pinned = false,
    )

    // toData

    @Test
    fun `toData maps value and eTLD1`() {
        val id = newItemId()
        val info = DomainInfo(value = "https://example.com", eTLD1 = "example.com")

        val entity = info.toData(id)

        assertEquals("https://example.com", entity.value)
        assertEquals("example.com", entity.eTLD1)
    }

    @Test
    fun `toData assigns provided passwordId`() {
        val id = newItemId()
        val entity = DomainInfo(value = "https://example.com", eTLD1 = null).toData(id)

        assertEquals(id, entity.passwordId)
    }

    @Test
    fun `toData preserves null eTLD1`() {
        val entity = DomainInfo(value = "https://example.com", eTLD1 = null).toData(newItemId())

        assertNull(entity.eTLD1)
    }

    // toDomain

    @Test
    fun `toDomain round-trips all fields`() {
        val id = newItemId()
        val info = DomainInfo(passwordId = id, value = "https://example.com", eTLD1 = "example.com")

        val entity = info.toData(id)
        val result = entity.toDomain()

        assertEquals(info, result)
    }

    // toDataDomainInfos

    @Test
    fun `toDataDomainInfos maps every DomainInfo to an entity`() {
        val id = newItemId()
        val infos = setOf(
            DomainInfo(value = "https://example.com", eTLD1 = "example.com"),
            DomainInfo(value = "https://other.com", eTLD1 = "other.com"),
        )

        val entities = password(id = id, domainInfos = infos).toDataDomainInfos(id)

        assertEquals(2, entities.size)
    }

    @Test
    fun `toDataDomainInfos assigns the given passwordId to all entities`() {
        val id = newItemId()
        val infos = setOf(
            DomainInfo(value = "https://a.com", eTLD1 = "a.com"),
            DomainInfo(value = "https://b.com", eTLD1 = "b.com"),
        )

        val entities = password(id = id, domainInfos = infos).toDataDomainInfos(id)

        assertTrue(entities.all { it.passwordId == id })
    }

    @Test
    fun `toDataDomainInfos on empty domainInfos returns empty set`() {
        val entities = password(domainInfos = emptySet()).toDataDomainInfos(newItemId())

        assertTrue(entities.isEmpty())
    }
}
