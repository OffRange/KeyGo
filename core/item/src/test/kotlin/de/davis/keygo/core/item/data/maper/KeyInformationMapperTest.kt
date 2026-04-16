package de.davis.keygo.core.item.data.maper

import kotlin.test.Test
import kotlin.test.assertContentEquals
import de.davis.keygo.core.item.data.local.entity.KeyInformation as EntityKeyInformation
import de.davis.keygo.core.item.domain.model.KeyInformation as DomainKeyInformation

class KeyInformationMapperTest {

    // toEntity

    @Test
    fun `toEntity copies wrappedKey bytes`() {
        val domain =
            DomainKeyInformation(wrappedKey = byteArrayOf(1, 2, 3), keyNonce = byteArrayOf())

        assertContentEquals(byteArrayOf(1, 2, 3), domain.toEntity().wrappedKey)
    }

    @Test
    fun `toEntity copies keyNonce bytes`() {
        val domain =
            DomainKeyInformation(wrappedKey = byteArrayOf(), keyNonce = byteArrayOf(4, 5, 6))

        assertContentEquals(byteArrayOf(4, 5, 6), domain.toEntity().keyNonce)
    }

    // toDomain

    @Test
    fun `toDomain copies wrappedKey bytes`() {
        val entity = EntityKeyInformation(wrappedKey = byteArrayOf(7, 8), keyNonce = byteArrayOf())

        assertContentEquals(byteArrayOf(7, 8), entity.toDomain().wrappedKey)
    }

    @Test
    fun `toDomain copies keyNonce bytes`() {
        val entity = EntityKeyInformation(wrappedKey = byteArrayOf(), keyNonce = byteArrayOf(9, 10))

        assertContentEquals(byteArrayOf(9, 10), entity.toDomain().keyNonce)
    }

    // round-trip

    @Test
    fun `domain round-trip preserves wrappedKey content`() {
        val original =
            DomainKeyInformation(wrappedKey = byteArrayOf(1, 2, 3), keyNonce = byteArrayOf(4))

        val roundTripped = original.toEntity().toDomain()

        assertContentEquals(original.wrappedKey, roundTripped.wrappedKey)
        assertContentEquals(original.keyNonce, roundTripped.keyNonce)
    }
}
