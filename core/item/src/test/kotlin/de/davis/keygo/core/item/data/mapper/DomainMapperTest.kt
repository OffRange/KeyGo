package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordCredential
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.PasswordSecret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DomainMapperTest {

    private fun login(
        id: ItemId = newItemId(),
        domainInfos: Set<DomainInfo> = emptySet(),
    ) = Login(
        id = id,
        username = null,
        domainInfos = domainInfos,
        passwordCredential = PasswordCredential(
            secret = PasswordSecret(EncryptedPayload.EMPTY),
            score = PasswordScore.Strong,
        ),
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
    fun `toData assigns provided loginId`() {
        val id = newItemId()
        val entity = DomainInfo(value = "https://example.com", eTLD1 = null).toData(id)

        assertEquals(id, entity.loginId)
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
        val info = DomainInfo(loginId = id, value = "https://example.com", eTLD1 = "example.com")

        val entity = info.toData(id)
        val result = entity.toDomain()

        assertEquals(info, result)
    }

    // toDomainInfoEntities

    @Test
    fun `toDomainInfoEntities maps every DomainInfo to an entity`() {
        val id = newItemId()
        val infos = setOf(
            DomainInfo(value = "https://example.com", eTLD1 = "example.com"),
            DomainInfo(value = "https://other.com", eTLD1 = "other.com"),
        )

        val entities = login(id = id, domainInfos = infos).toDomainInfoEntities(id)

        assertEquals(2, entities.size)
    }

    @Test
    fun `toDomainInfoEntities assigns the given loginId to all entities`() {
        val id = newItemId()
        val infos = setOf(
            DomainInfo(value = "https://a.com", eTLD1 = "a.com"),
            DomainInfo(value = "https://b.com", eTLD1 = "b.com"),
        )

        val entities = login(id = id, domainInfos = infos).toDomainInfoEntities(id)

        assertTrue(entities.all { it.loginId == id })
    }

    @Test
    fun `toDomainInfoEntities on empty domainInfos returns empty set`() {
        val entities = login(domainInfos = emptySet()).toDomainInfoEntities(newItemId())

        assertTrue(entities.isEmpty())
    }
}
