package de.davis.keygo.feature.list_screen.domain.usecase

import de.davis.keygo.core.item.FakeCreditCardRepository
import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.VaultId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.CredentialType
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasskeyRef
import de.davis.keygo.core.item.domain.model.PasswordCredential
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.core.item.domain.model.Tag
import de.davis.keygo.core.item.domain.model.Timestamp
import de.davis.keygo.core.item.passkeyRef
import de.davis.keygo.core.util.domain.usecase.SortUseCase
import de.davis.keygo.feature.list_screen.domain.model.FilterFacet
import de.davis.keygo.feature.list_screen.domain.model.FilterResult
import de.davis.keygo.feature.list_screen.domain.model.FilterState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveFilterResultUseCaseTest {

    private val loginRepository = FakeLoginRepository()
    private val itemRepository = FakeItemRepository(loginRepository)
    private val creditCardRepository = FakeCreditCardRepository()
    private val sortUseCase = SortUseCase()

    private val useCase = ObserveFilterResultUseCase(
        itemRepository = itemRepository,
        loginRepository = loginRepository,
        filterUseCase = FilterUseCase(sortUseCase),
        observeCardExpiryStatuses = ObserveCardExpiryStatusesUseCase(creditCardRepository),
        availableFacetValues = AvailableFacetValuesUseCase(),
    )

    private fun login(
        name: String,
        id: ItemId = newItemId(),
        vault: VaultId = newVaultId(),
        tags: Set<Tag> = emptySet(),
        passwordCredential: PasswordCredential? = null,
        passkeys: Set<PasskeyRef> = emptySet(),
    ) = Login(
        id = id,
        name = name,
        username = null,
        domainInfos = emptySet(),
        passwordCredential = passwordCredential,
        totp = null,
        passkeys = passkeys,
        note = null,
        pinned = false,
        vaultId = vault,
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        timestamp = Timestamp(),
        tags = tags,
    )

    private fun credential(score: PasswordScore) =
        PasswordCredential(secret = PasswordSecret(EncryptedPayload.EMPTY), score = score)

    @Test
    fun `filters by a tag seeded in the fake store`() = runTest {
        val bank = Tag.of("Bank")!!
        val tagged = login("Tagged", tags = setOf(bank))
        val untagged = login("Untagged")
        loginRepository.seed(tagged, untagged)

        val items = MutableStateFlow(listOf(tagged, untagged))
        val filterState = MutableStateFlow(
            FilterState().with(FilterFacet.Tags, setOf(bank)),
        )

        val result = useCase(items, filterState).first()

        assertEquals(listOf(tagged.id), result.items.map { it.id })
    }

    @Test
    fun `available reflects the unfiltered items, so a value the filter hides is still offered`() =
        runTest {
            val bank = Tag.of("Bank")!!
            val work = Tag.of("Work")!!
            val tagged = login("Tagged", tags = setOf(bank))
            val other = login("Other", tags = setOf(work))
            loginRepository.seed(tagged, other)

            val items = MutableStateFlow(listOf(tagged, other))
            val filterState = MutableStateFlow(
                FilterState().with(FilterFacet.Tags, setOf(bank)),
            )

            val result = useCase(items, filterState).first()

            assertEquals(listOf(tagged.id), result.items.map { it.id })
            assertEquals(setOf(bank, work), result.available[FilterFacet.Tags])
        }

    @Test
    fun `a filter hiding every item reports empty because of the filter`() = runTest {
        val loose = login("Loose")
        loginRepository.seed(loose)

        val items = MutableStateFlow(listOf(loose))
        val filterState = MutableStateFlow(
            FilterState().with(FilterFacet.Pinned, setOf(true)),
        )

        val result = useCase(items, filterState).first()

        assertTrue(result.items.isEmpty())
        assertTrue(result.isEmptyBecauseOfFilter)
    }

    @Test
    fun `an empty source is not empty because of the filter`() = runTest {
        val items = MutableStateFlow(emptyList<Login>())
        val filterState = MutableStateFlow(
            FilterState().with(FilterFacet.Pinned, setOf(true)),
        )

        val result = useCase(items, filterState).first()

        assertTrue(result.items.isEmpty())
        assertFalse(result.isEmptyBecauseOfFilter)
    }

    @Test
    fun `re-emits when a password score changes in the login repository`() = runTest {
        val item = login("Scored", passwordCredential = credential(PasswordScore.Weak))
        loginRepository.seed(item)

        val items = MutableStateFlow(listOf(item))
        val filterState = MutableStateFlow(FilterState())

        val results = mutableListOf<FilterResult<Login>>()
        val job = launch { useCase(items, filterState).toList(results) }

        runCurrent()
        loginRepository.seed(item.copy(passwordCredential = credential(PasswordScore.Excellent)))
        runCurrent()

        job.cancel()

        assertTrue(results.size >= 2)
        assertEquals(
            setOf(PasswordScore.Weak),
            results.first().available[FilterFacet.PasswordScores]
        )
        assertEquals(
            setOf(PasswordScore.Excellent),
            results.last().available[FilterFacet.PasswordScores]
        )
    }

    @Test
    fun `filters by a credential the login repository reports`() = runTest {
        val withPasskey = login("With passkey", passkeys = setOf(passkeyRef("example.com")))
        val without = login("Without", passwordCredential = credential(PasswordScore.Strong))
        loginRepository.seed(withPasskey, without)

        val items = MutableStateFlow(listOf(withPasskey, without))
        val filterState = MutableStateFlow(
            FilterState().with(FilterFacet.Credentials, setOf(CredentialType.Passkey)),
        )

        val result = useCase(items, filterState).first()

        assertEquals(listOf(withPasskey.id), result.items.map { it.id })
        assertEquals(
            setOf(CredentialType.Passkey, CredentialType.Password),
            result.available[FilterFacet.Credentials],
        )
    }
}
