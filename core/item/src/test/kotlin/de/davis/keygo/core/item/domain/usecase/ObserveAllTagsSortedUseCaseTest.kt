package de.davis.keygo.core.item.domain.usecase

import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.model.Timestamp
import de.davis.keygo.core.util.domain.usecase.SortUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ObserveAllTagsSortedUseCaseTest {

    private val loginRepository = FakeLoginRepository()
    private val itemRepository = FakeItemRepository(loginRepository)
    private val useCase = ObserveAllTagsSortedUseCase(
        itemRepository = itemRepository,
        sortUseCase = SortUseCase(),
    )

    private fun tag(value: String): Tag = Tag.of(value)!!

    private fun login(name: String, tags: Set<Tag>) = Login(
        username = null,
        domainInfos = emptySet(),
        passwordCredential = null,
        totp = null,
        vaultId = newVaultId(),
        name = name,
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        tags = tags,
        note = null,
        pinned = false,
        timestamp = Timestamp(),
    )

    @Test
    fun `emits all distinct tags sorted naturally and case-insensitively`() = runTest {
        loginRepository.seed(
            login("a", setOf(tag("Zebra"), tag("apple"))),
            login("b", setOf(tag("item10"), tag("item2"))),
        )

        val result = useCase().first()

        assertEquals(listOf(tag("apple"), tag("item2"), tag("item10"), tag("Zebra")), result)
    }

    @Test
    fun `emits empty list when no tags exist`() = runTest {
        val result = useCase().first()
        assertEquals(emptyList(), result)
    }
}
