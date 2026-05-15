package de.davis.keygo.feature.autofill.domain.usecase

import de.davis.keygo.core.feature.autofill.FakeRegistrableDomainResolver
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DoesItemHaveDomainReferencesUseCaseTest {

    private val vaultId = newVaultId()
    private val loginRepository = FakeLoginRepository()
    private val registrableDomainResolver = FakeRegistrableDomainResolver()
    private lateinit var useCase: DoesItemHaveDomainReferencesUseCase

    @BeforeTest
    fun setup() {
        useCase = DoesItemHaveDomainReferencesUseCase(
            loginRepository,
            registrableDomainResolver,
        )
    }

    private fun makeLogin(
        id: ItemId = newItemId(),
        domainInfos: Set<DomainInfo> = emptySet(),
    ) = Login(
        id = id,
        name = "Test",
        username = null,
        domainInfos = domainInfos,
        passwordCredential = null,
        totp = null,
        note = null,
        pinned = false,
        vaultId = vaultId,
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
    )

    @Test
    fun `blank domain returns false`() = runTest {
        val itemId = newItemId()
        loginRepository.seed(makeLogin(id = itemId))

        val result = useCase(itemId, " ")

        assertFalse(result)
    }

    @Test
    fun `login not found returns false`() = runTest {
        val nonExistentId = newItemId()

        val result = useCase(nonExistentId, "example.com")

        assertFalse(result)
    }

    @Test
    fun `login found but no domain infos returns false`() = runTest {
        val itemId = newItemId()
        loginRepository.seed(makeLogin(id = itemId, domainInfos = emptySet()))

        val result = useCase(itemId, "example.com")

        assertFalse(result)
    }

    @Test
    fun `match on exact value returns true`() = runTest {
        val itemId = newItemId()
        val domain = "example.com"
        val domainInfo = DomainInfo(value = domain, eTLD1 = "example.com")
        loginRepository.seed(makeLogin(id = itemId, domainInfos = setOf(domainInfo)))
        registrableDomainResolver.resolutions = mapOf(domain to "example.com")

        val result = useCase(itemId, domain)

        assertTrue(result)
    }

    @Test
    fun `match on eTLD1 returns true`() = runTest {
        val itemId = newItemId()
        val domain = "sub.example.com"
        val eTLD1 = "example.com"
        val domainInfo = DomainInfo(value = "different.com", eTLD1 = eTLD1)
        loginRepository.seed(makeLogin(id = itemId, domainInfos = setOf(domainInfo)))
        registrableDomainResolver.resolutions = mapOf(domain to eTLD1)

        val result = useCase(itemId, domain)

        assertTrue(result)
    }

    @Test
    fun `no match returns false`() = runTest {
        val itemId = newItemId()
        val domain = "newdomain.com"
        val domainInfo = DomainInfo(value = "otherdomain.com", eTLD1 = "otherdomain.com")
        loginRepository.seed(makeLogin(id = itemId, domainInfos = setOf(domainInfo)))
        registrableDomainResolver.resolutions = mapOf(domain to "newdomain.com")

        val result = useCase(itemId, domain)

        assertFalse(result)
    }
}
