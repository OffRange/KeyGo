package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.VaultContext
import kotlin.test.Test
import kotlin.test.assertEquals

class VaultContextMapperTest {

    @Test
    fun `null id maps to NoSpecific`() {
        val context: VaultContext = (null as VaultId?).toDomain()

        assertEquals(VaultContext.NoSpecific, context)
    }

    @Test
    fun `non-null id maps to ById preserving the id`() {
        val id = newVaultId()

        val context = id.toDomain()

        assertEquals(VaultContext.ById(id), context)
    }
}
