package de.davis.keygo.core.item.data.mapper

import de.davis.keygo.core.item.data.local.entity.ItemEntity
import de.davis.keygo.core.item.data.local.entity.LoginEntity
import de.davis.keygo.core.item.data.local.entity.TagEntity
import de.davis.keygo.core.item.data.local.entity.credential.PasswordEntity
import de.davis.keygo.core.item.data.local.pojo.ItemProjection
import de.davis.keygo.core.item.data.local.pojo.LoginProjection
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordCredential
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.core.item.generated.domain.model.VaultItemType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import de.davis.keygo.core.item.data.local.entity.KeyInformation as EntityKeyInformation

class LoginMapperTest {

    @Test
    fun `LoginProjection with null passwordEntity maps to Login with null passwordCredential`() {
        val projection = baseProjection(passwordEntity = null)
        val login = projection.toDomain()
        assertNull(login.passwordCredential)
    }

    @Test
    fun `Login with null passwordCredential maps to null PasswordEntity`() {
        val login = baseLogin(passwordCredential = null)
        assertNull(login.toPasswordEntity())
    }

    @Test
    fun `Login with passwordCredential maps to PasswordEntity preserving score and payload`() {
        val payload = EncryptedPayload(byteArrayOf(1), byteArrayOf(2))
        val login = baseLogin(
            passwordCredential = PasswordCredential(
                secret = PasswordSecret(payload),
                score = PasswordScore.Excellent,
            ),
        )
        val entity = login.toPasswordEntity()!!
        assertEquals(login.id, entity.loginId)
        assertEquals(PasswordScore.Excellent, entity.passwordScore)
        assertEquals(payload, entity.password)
    }

    @Test
    fun `toDomain maps tag entity values to domain tags`() {
        val id = newItemId()
        val projection = baseProjection(
            id = id,
            passwordEntity = null,
            tags = setOf(
                TagEntity(id = 1, value = "Work", normalized = "work"),
                TagEntity(id = 2, value = "Email", normalized = "email"),
            ),
        )

        val login = projection.toDomain()

        assertEquals(setOf("Work", "Email"), login.tags)
    }

    @Test
    fun `toTagEntities trims, drops blanks, dedups case-insensitively`() {
        val result = listOf(" Work ", "work", "", "Email").toTagDomains()

        assertEquals(
            listOf("Work" to "work", "Email" to "email"),
            result.map { it.value to it.normalized },
        )
    }

    private fun baseProjection(
        id: ItemId = newItemId(),
        passwordEntity: PasswordEntity?,
        tags: Set<TagEntity> = emptySet(),
    ): LoginProjection = LoginProjection(
        loginEntity = LoginEntity(id = id, username = "alice"),
        item = ItemProjection(
            itemEntity = ItemEntity(
                id = id,
                vaultId = newVaultId(),
                name = "Test",
                note = null,
                itemType = VaultItemType.Login,
                pinned = false,
                keyInformation = EntityKeyInformation(
                    wrappedKey = byteArrayOf(),
                    keyNonce = byteArrayOf(),
                ),
            ),
            tags = tags,
        ),
        passwordEntity = passwordEntity,
        rpEntity = emptyList(),
        domains = emptyList(),
        totp = null,
    )

    private fun baseLogin(
        passwordCredential: PasswordCredential? = null,
    ): Login = Login(
        id = newItemId(),
        username = "alice",
        domainInfos = emptySet(),
        passwordCredential = passwordCredential,
        totp = null,
        vaultId = newVaultId(),
        name = "Test",
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        note = null,
        pinned = false,
    )
}
