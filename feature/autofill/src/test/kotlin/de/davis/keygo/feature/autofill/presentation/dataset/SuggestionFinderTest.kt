package de.davis.keygo.feature.autofill.presentation.dataset

import de.davis.keygo.core.feature.autofill.FakeRegistrableDomainResolver
import de.davis.keygo.core.feature.autofill.autofillId
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.PasswordCredential
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.item.domain.model.PasswordSecret
import de.davis.keygo.core.item.domain.model.Timestamp
import de.davis.keygo.core.item.domain.model.Totp
import de.davis.keygo.core.security.domain.usecase.GetTdlMatchedLoginsUseCase
import de.davis.keygo.feature.autofill.presentation.model.FieldType
import de.davis.keygo.feature.autofill.presentation.model.Form
import de.davis.keygo.feature.autofill.presentation.model.FormField
import de.davis.keygo.feature.autofill.presentation.model.FormType
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
internal class SuggestionFinderTest {

    private val loginRepo = FakeLoginRepository()
    private val resolver = FakeRegistrableDomainResolver()
    private val useCase = GetTdlMatchedLoginsUseCase(resolver, loginRepo)
    private val finder = SuggestionFinder(useCase)

    private val vaultId = newVaultId()
    private val keyInfo = KeyInformation(byteArrayOf(), byteArrayOf())
    private val minimalPassword = PasswordCredential(
        secret = PasswordSecret(EncryptedPayload.EMPTY),
        score = PasswordScore.None,
    )
    private val minimalTotp = Totp(
        loginId = newItemId(),
        secret = Totp.Secret(EncryptedPayload.EMPTY),
    )

    private val lUserOnly = Login(
        id = newItemId(),
        username = "alice",
        domainInfos = setOf(DomainInfo(value = "https://example.com", eTLD1 = "example.com")),
        passwordCredential = null,
        totp = null,
        passkeys = emptySet(),
        vaultId = vaultId,
        name = "UserOnly",
        keyInformation = keyInfo,
        note = null,
        pinned = false,
        timestamp = Timestamp(),
    )
    private val lPassOnly = Login(
        id = newItemId(),
        username = null,
        domainInfos = setOf(DomainInfo(value = "https://example.com", eTLD1 = "example.com")),
        passwordCredential = minimalPassword,
        totp = null,
        passkeys = emptySet(),
        vaultId = vaultId,
        name = "PassOnly",
        keyInformation = keyInfo,
        note = null,
        pinned = false,
        timestamp = Timestamp(),
    )
    private val lBoth = Login(
        id = newItemId(),
        username = "bob",
        domainInfos = setOf(DomainInfo(value = "https://example.com", eTLD1 = "example.com")),
        passwordCredential = minimalPassword,
        totp = null,
        passkeys = emptySet(),
        vaultId = vaultId,
        name = "Both",
        keyInformation = keyInfo,
        note = null,
        pinned = false,
        timestamp = Timestamp(),
    )
    private val lNeither = Login(
        id = newItemId(),
        username = null,
        domainInfos = setOf(DomainInfo(value = "https://other.com", eTLD1 = "other.com")),
        passwordCredential = null,
        totp = minimalTotp,
        passkeys = emptySet(),
        vaultId = vaultId,
        name = "Neither",
        keyInformation = keyInfo,
        note = null,
        pinned = false,
        timestamp = Timestamp(),
    )

    @Before
    fun setUp() {
        loginRepo.seed(lUserOnly, lPassOnly, lBoth, lNeither)
        resolver.resolutions = mapOf("https://example.com" to "example.com")
    }

    private fun credentialsForm(
        fields: List<FormField>,
        url: String? = "https://example.com",
    ) = Form(
        type = FormType.Credentials,
        fields = fields,
        url = url,
        isBrowser = false,
        appPackageName = "com.example",
        isSuspicious = false,
    )

    private fun field(type: FieldType, viewId: Int = 1) = FormField(
        autofillId = autofillId(viewId),
        type = type,
        focused = false,
    )

    @Test
    fun `username-only form returns logins with a username`() = runTest {
        val form = credentialsForm(listOf(field(FieldType.Credentials.Username)))
        val result = finder.findVaultSuggestions(form, count = 10)
        assertEquals(setOf(lUserOnly.id, lBoth.id), result.map { it.id }.toSet())
    }

    @Test
    fun `email-only form returns logins with a username`() = runTest {
        val form = credentialsForm(listOf(field(FieldType.Credentials.EMail)))
        val result = finder.findVaultSuggestions(form, count = 10)
        assertEquals(setOf(lUserOnly.id, lBoth.id), result.map { it.id }.toSet())
    }

    @Test
    fun `phone-only form returns logins with a username`() = runTest {
        val form = credentialsForm(listOf(field(FieldType.Credentials.Phone)))
        val result = finder.findVaultSuggestions(form, count = 10)
        assertEquals(setOf(lUserOnly.id, lBoth.id), result.map { it.id }.toSet())
    }

    @Test
    fun `password-only form returns logins with a password`() = runTest {
        val form = credentialsForm(listOf(field(FieldType.Credentials.Password)))
        val result = finder.findVaultSuggestions(form, count = 10)
        assertEquals(setOf(lPassOnly.id, lBoth.id), result.map { it.id }.toSet())
    }

    @Test
    fun `mixed form returns both username and password logins`() = runTest {
        val form = credentialsForm(
            listOf(
                field(FieldType.Credentials.Username, viewId = 1),
                field(FieldType.Credentials.Password, viewId = 2),
            ),
        )
        val result = finder.findVaultSuggestions(form, count = 10)
        assertEquals(setOf(lUserOnly.id, lPassOnly.id, lBoth.id), result.map { it.id }.toSet())
    }

    @Test
    fun `count of zero returns empty and resolves nothing`() = runTest {
        val form = credentialsForm(listOf(field(FieldType.Credentials.Username)))
        val result = finder.findVaultSuggestions(form, count = 0)
        assertTrue(result.isEmpty())
        assertTrue(resolver.resolvedDomains.isEmpty())
    }

    @Test
    fun `form with null url returns empty`() = runTest {
        val form = credentialsForm(listOf(field(FieldType.Credentials.Username)), url = null)
        val result = finder.findVaultSuggestions(form, count = 10)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `resolver returning null returns empty`() = runTest {
        resolver.resolutions = mapOf("https://example.com" to null)
        val form = credentialsForm(listOf(field(FieldType.Credentials.Username)))
        val result = finder.findVaultSuggestions(form, count = 10)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `limit is honored`() = runTest {
        val extra1 = Login(
            id = newItemId(),
            username = "carol",
            domainInfos = setOf(DomainInfo(value = "https://example.com", eTLD1 = "example.com")),
            passwordCredential = null,
            totp = null,
            passkeys = emptySet(),
            vaultId = vaultId,
            name = "Extra1",
            keyInformation = keyInfo,
            note = null,
            pinned = false,
            timestamp = Timestamp(),
        )
        val extra2 = Login(
            id = newItemId(),
            username = "dave",
            domainInfos = setOf(DomainInfo(value = "https://example.com", eTLD1 = "example.com")),
            passwordCredential = null,
            totp = null,
            passkeys = emptySet(),
            vaultId = vaultId,
            name = "Extra2",
            keyInformation = keyInfo,
            note = null,
            pinned = false,
            timestamp = Timestamp(),
        )
        val extra3 = Login(
            id = newItemId(),
            username = "eve",
            domainInfos = setOf(DomainInfo(value = "https://example.com", eTLD1 = "example.com")),
            passwordCredential = null,
            totp = null,
            passkeys = emptySet(),
            vaultId = vaultId,
            name = "Extra3",
            keyInformation = keyInfo,
            note = null,
            pinned = false,
            timestamp = Timestamp(),
        )
        loginRepo.seed(extra1, extra2, extra3)
        val form = credentialsForm(listOf(field(FieldType.Credentials.Username)))
        val result = finder.findVaultSuggestions(form, count = 2)
        assertEquals(2, result.size)
    }
}
