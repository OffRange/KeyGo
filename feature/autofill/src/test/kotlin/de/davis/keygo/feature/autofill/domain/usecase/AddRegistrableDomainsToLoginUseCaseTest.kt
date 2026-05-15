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
import kotlin.test.assertEquals

class AddRegistrableDomainsToLoginUseCaseTest {

    private val vaultId = newVaultId()
    private val loginRepository = FakeLoginRepository()
    private val registrableDomainResolver = FakeRegistrableDomainResolver()
    private lateinit var useCase: AddRegistrableDomainsToLoginUseCase

    @BeforeTest
    fun setup() {
        useCase = AddRegistrableDomainsToLoginUseCase(
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
    fun `stores domain info with resolved eTLD1`() = runTest {
        val loginId = newItemId()
        val domain = "example.com"
        val resolvedETLD1 = "example.com"
        loginRepository.seed(makeLogin(id = loginId))
        registrableDomainResolver.resolutions = mapOf(domain to resolvedETLD1)

        useCase(loginId, domain)

        val updatedLogin = loginRepository.getLoginById(loginId)
        assertEquals(1, updatedLogin?.domainInfos?.size)
        val domainInfo = updatedLogin?.domainInfos?.first()
        assertEquals(domain, domainInfo?.value)
        assertEquals(resolvedETLD1, domainInfo?.eTLD1)
    }

    @Test
    fun `stores null eTLD1 when resolver returns null`() = runTest {
        val loginId = newItemId()
        val domain = "invalid"
        loginRepository.seed(makeLogin(id = loginId))
        registrableDomainResolver.resolutions = mapOf(domain to null)

        useCase(loginId, domain)

        val updatedLogin = loginRepository.getLoginById(loginId)
        assertEquals(1, updatedLogin?.domainInfos?.size)
        val domainInfo = updatedLogin?.domainInfos?.first()
        assertEquals(domain, domainInfo?.value)
        assertEquals(null, domainInfo?.eTLD1)
    }
}
