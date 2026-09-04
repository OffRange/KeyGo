package de.davis.keygo.feature.autofill.presentation.activity

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import de.davis.keygo.core.feature.autofill.FakeAutofillDatasetProvider
import de.davis.keygo.core.feature.autofill.FakeDigitalAssetLinkRepository
import de.davis.keygo.core.feature.autofill.FakeSignatureInfoProvider
import de.davis.keygo.core.feature.autofill.FakeSmsCodeRepository
import de.davis.keygo.core.feature.autofill.FakeTotpGenerator
import de.davis.keygo.core.feature.autofill.FakeTotpRepository
import de.davis.keygo.core.feature.autofill.autofillId
import de.davis.keygo.core.item.FakeItemRepository
import de.davis.keygo.core.item.FakeLoginRepository
import de.davis.keygo.core.item.FakeVaultRepository
import de.davis.keygo.core.item.domain.alias.ItemId
import de.davis.keygo.core.item.domain.alias.newItemId
import de.davis.keygo.core.item.domain.alias.newVaultId
import de.davis.keygo.core.item.domain.model.DomainInfo
import de.davis.keygo.core.item.domain.model.EncryptedPayload
import de.davis.keygo.core.item.domain.model.KeyInformation
import de.davis.keygo.core.item.domain.model.Login
import de.davis.keygo.core.item.domain.model.Timestamp
import de.davis.keygo.core.item.domain.model.Totp
import de.davis.keygo.core.security.crypto.FakeCryptographicScopeProvider
import de.davis.keygo.core.util.FakeRegistrableDomainResolver
import de.davis.keygo.core.util.Result
import de.davis.keygo.feature.autofill.domain.usecase.AddRegistrableDomainsToLoginUseCase
import de.davis.keygo.feature.autofill.domain.usecase.DoesItemHaveDomainReferencesUseCase
import de.davis.keygo.feature.autofill.domain.usecase.IsAppLinkedToWebsiteUseCase
import de.davis.keygo.feature.autofill.presentation.activity.model.AssociationDialogVisibility
import de.davis.keygo.feature.autofill.presentation.activity.model.AutofillEvent
import de.davis.keygo.feature.autofill.presentation.activity.model.AutofillUiEvent
import de.davis.keygo.feature.autofill.presentation.activity.model.SuspicionDialogVisibility
import de.davis.keygo.feature.autofill.presentation.activity.model.SuspicionReason
import de.davis.keygo.feature.autofill.presentation.model.FieldType
import de.davis.keygo.feature.autofill.presentation.model.FillRequestData
import de.davis.keygo.feature.autofill.presentation.model.Form
import de.davis.keygo.feature.autofill.presentation.model.FormField
import de.davis.keygo.feature.autofill.presentation.model.FormType
import de.davis.keygo.feature.autofill.presentation.model.Request
import de.davis.keygo.feature.autofill.presentation.model.RequestData
import de.davis.keygo.feature.autofill.presentation.model.SaveRequestData
import de.davis.keygo.feature.autofill.presentation.sms.SmsCodeFailure
import de.davis.keygo.feature.totp.domain.model.TotpError
import de.davis.keygo.feature.totp.domain.model.TotpValue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
internal class AutofillViewModelTest {

    private lateinit var vaultRepo: FakeVaultRepository
    private lateinit var loginRepo: FakeLoginRepository
    private lateinit var fakeItemRepo: FakeItemRepository
    private lateinit var totpRepo: FakeTotpRepository
    private lateinit var cryptoProvider: FakeCryptographicScopeProvider
    private lateinit var datasetProvider: FakeAutofillDatasetProvider
    private lateinit var resolver: FakeRegistrableDomainResolver
    private lateinit var signatureProvider: FakeSignatureInfoProvider
    private lateinit var dalRepo: FakeDigitalAssetLinkRepository
    private lateinit var totpGenerator: FakeTotpGenerator
    private lateinit var smsCodeRepo: FakeSmsCodeRepository

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUpMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @After
    fun tearDownMainDispatcher() {
        Dispatchers.resetMain()
    }

    @Before
    fun setUp() {
        loginRepo = FakeLoginRepository()
        vaultRepo = FakeVaultRepository()
        fakeItemRepo = FakeItemRepository(loginRepo)
        totpRepo = FakeTotpRepository()
        cryptoProvider = FakeCryptographicScopeProvider(fakeItemRepo)
        datasetProvider = FakeAutofillDatasetProvider()
        resolver = FakeRegistrableDomainResolver()
        signatureProvider = FakeSignatureInfoProvider()
        dalRepo = FakeDigitalAssetLinkRepository()
        totpGenerator = FakeTotpGenerator()
        smsCodeRepo = FakeSmsCodeRepository()
    }

    private fun buildVm(requestData: RequestData): AutofillViewModel {
        val handle =
            SavedStateHandle(mapOf(AutofillViewModel.KEY_AUTOFILL_INFORMATION to requestData))
        return AutofillViewModel(
            savedStateHandle = handle,
            vaultRepository = vaultRepo,
            loginRepository = loginRepo,
            totpRepository = totpRepo,
            itemRepository = fakeItemRepo,
            smsCodeRepository = smsCodeRepo,
            cryptographicScopeProvider = cryptoProvider,
            autofillDatasetProvider = datasetProvider,
            doesItemHaveDomainReferences = DoesItemHaveDomainReferencesUseCase(loginRepo, resolver),
            addRegistrableDomainToLogin = AddRegistrableDomainsToLoginUseCase(loginRepo, resolver),
            isAppLinkedToWebsite = IsAppLinkedToWebsiteUseCase(dalRepo, signatureProvider),
            totpGenerator = totpGenerator,
        )
    }

    private fun form(
        fields: List<FormField> = emptyList(),
        url: String? = "https://example.com",
        isSuspicious: Boolean = false,
        type: FormType = FormType.Credentials,
        appPackageName: String = "com.example",
    ) = Form(type, fields, url, isBrowser = false, appPackageName, isSuspicious)

    private fun credField(type: FieldType, viewId: Int = 1, autofillValue: String? = null) =
        FormField(
            autofillId = autofillId(viewId),
            type = type,
            focused = false,
            url = "https://example.com",
            autofillValue = autofillValue,
        )

    private fun smsOtpRequest(fields: List<FormField>) = FillRequestData.SmsOtp(
        form(fields = fields, type = FormType.TOTP),
    )

    private fun testIntentSender(): IntentSender = PendingIntent.getActivity(
        RuntimeEnvironment.getApplication(),
        0,
        Intent(),
        PendingIntent.FLAG_IMMUTABLE,
    ).intentSender

    private fun testLogin(
        username: String? = "alice",
        name: String = username ?: "Login",
        domainInfos: Set<DomainInfo> = emptySet(),
    ) = Login(
        id = newItemId(),
        username = username,
        domainInfos = domainInfos,
        passwordCredential = null,
        totp = null,
        passkeys = emptySet(),
        vaultId = newVaultId(),
        name = name,
        keyInformation = KeyInformation(byteArrayOf(), byteArrayOf()),
        note = null,
        pinned = false,
        timestamp = Timestamp(),
    )

    private fun minimalTotp(loginId: ItemId) = Totp(
        loginId = loginId,
        secret = Totp.Secret(EncryptedPayload.EMPTY),
    )

    private fun matchingDomainInfo(loginId: ItemId? = null) = DomainInfo(
        loginId = loginId,
        value = "https://example.com",
        eTLD1 = "example.com",
    )

    @Test
    fun `suspicious form not linked to website shows suspicion dialog`() = runTest {
        signatureProvider.signatures = emptyMap()
        val requestData = FillRequestData.App(
            form(isSuspicious = true, url = "https://example.com", appPackageName = "com.example"),
        )
        val vm = buildVm(requestData)
        vm.start()

        val visibility = vm.uiState.value.suspicionDialogVisibility
        assertIs<SuspicionDialogVisibility.Visible>(visibility)
        assertEquals(SuspicionReason.NotLinked, visibility.reason)
        assertEquals(Request.None, vm.uiState.value.request)
    }

    @Test
    fun `non-suspicious form does not show suspicion dialog`() = runTest {
        val requestData = FillRequestData.App(form(isSuspicious = false))
        val vm = buildVm(requestData)
        vm.start()

        assertEquals(SuspicionDialogVisibility.Hidden, vm.uiState.value.suspicionDialogVisibility)
        assertEquals(Request.SelectItem, vm.uiState.value.request)
    }

    @Test
    fun `suspicious form linked to website shows no suspicion dialog`() = runTest {
        signatureProvider.signatures = mapOf("com.example" to setOf("sig1"))
        dalRepo.linkedTriples = setOf(Triple("com.example", "sig1", "https://example.com"))
        val requestData = FillRequestData.App(
            form(isSuspicious = true, url = "https://example.com", appPackageName = "com.example"),
        )
        val vm = buildVm(requestData)
        vm.start()

        assertEquals(SuspicionDialogVisibility.Hidden, vm.uiState.value.suspicionDialogVisibility)
        assertEquals(Request.SelectItem, vm.uiState.value.request)
    }

    @Test
    fun `suspicious form whose lookup fails shows the unverified dialog`() = runTest {
        signatureProvider.signatures = mapOf("com.example" to setOf("sig1"))
        dalRepo.linkedTriples = setOf(Triple("com.example", "sig1", "https://example.com"))
        dalRepo.failingSignatures = setOf("sig1")
        val requestData = FillRequestData.App(
            form(isSuspicious = true, url = "https://example.com", appPackageName = "com.example"),
        )
        val vm = buildVm(requestData)
        vm.start()

        val visibility = vm.uiState.value.suspicionDialogVisibility
        assertIs<SuspicionDialogVisibility.Visible>(visibility)
        assertEquals(SuspicionReason.Unverified, visibility.reason)
        assertEquals(Request.None, vm.uiState.value.request)
    }

    @Test
    fun `continuing past suspicion hides dialog and proceeds`() = runTest {
        signatureProvider.signatures = emptyMap()
        val requestData = FillRequestData.App(
            form(isSuspicious = true, url = "https://example.com", appPackageName = "com.example"),
        )
        val vm = buildVm(requestData)
        vm.start()

        assertIs<SuspicionDialogVisibility.Visible>(vm.uiState.value.suspicionDialogVisibility)

        vm.onEvent(AutofillUiEvent.OnContinueInSuspicion)

        assertEquals(SuspicionDialogVisibility.Hidden, vm.uiState.value.suspicionDialogVisibility)
        assertEquals(Request.SelectItem, vm.uiState.value.request)
    }

    @Test
    fun `save request data sets request to SaveItem`() = runTest {
        val fields = listOf(
            credField(FieldType.Credentials.Username, viewId = 1, autofillValue = "user"),
            credField(FieldType.Credentials.Password, viewId = 2, autofillValue = "pass"),
        )
        val requestData = SaveRequestData(form(fields = fields, isSuspicious = false))
        val vm = buildVm(requestData)
        vm.start()

        assertIs<Request.SaveItem>(vm.uiState.value.request)
    }

    @Test
    fun `app request data sets request to SelectItem`() = runTest {
        val requestData = FillRequestData.App(form(isSuspicious = false))
        val vm = buildVm(requestData)
        vm.start()

        assertEquals(Request.SelectItem, vm.uiState.value.request)
    }

    @Test
    fun `generate password request shows generate password`() = runTest {
        val fields = listOf(credField(FieldType.Credentials.Password))
        val requestData =
            FillRequestData.GeneratePassword(form(fields = fields, isSuspicious = false))
        val vm = buildVm(requestData)
        vm.start()

        assertTrue(vm.uiState.value.showGeneratePassword)
    }

    @Test
    fun `generated password sends Fill with copy to clipboard`() = runTest {
        val fields = listOf(credField(FieldType.Credentials.Password, viewId = 1))
        val requestData =
            FillRequestData.GeneratePassword(form(fields = fields, isSuspicious = false))
        val vm = buildVm(requestData)
        vm.start()

        val eventDeferred = async { vm.events.first() }
        vm.onEvent(AutofillUiEvent.OnGeneratedPassword("SecurePass123!"))
        val event = eventDeferred.await()

        assertIs<AutofillEvent.Fill>(event)
        assertEquals("SecurePass123!", event.copyToClipboard)
    }

    @Test
    fun `generated password with no password fields aborts`() = runTest {
        val fields = listOf(credField(FieldType.Credentials.Username, viewId = 1))
        val requestData =
            FillRequestData.GeneratePassword(form(fields = fields, isSuspicious = false))
        val vm = buildVm(requestData)
        vm.start()

        val eventDeferred = async { vm.events.first() }
        vm.onEvent(AutofillUiEvent.OnGeneratedPassword("anyPass"))

        assertEquals(AutofillEvent.Abort, eventDeferred.await())
    }

    @Test
    fun `selecting item with domain reference sends Fill event`() = runTest {
        resolver.resolutions = mapOf("https://example.com" to "example.com")
        val login = testLogin(
            username = "alice",
            domainInfos = setOf(matchingDomainInfo()),
        )
        loginRepo.seed(login)

        val fields = listOf(credField(FieldType.Credentials.Username, viewId = 1))
        val requestData = FillRequestData.App(
            form(fields = fields, url = "https://example.com", isSuspicious = false),
        )
        val vm = buildVm(requestData)
        vm.start()

        val eventDeferred = async { vm.events.first() }
        vm.onEvent(AutofillUiEvent.OnItemSelected(login.id))
        val event = eventDeferred.await()

        assertIs<AutofillEvent.Fill>(event)
        val lastCall = datasetProvider.getFillingDatasetCalls.last()
        assertTrue(lastCall.any { it.value == "alice" })
    }

    @Test
    fun `selecting item without domain reference shows association dialog`() = runTest {
        val login = testLogin(username = "bob", name = "bob", domainInfos = emptySet())
        loginRepo.seed(login)

        val requestData = FillRequestData.App(
            form(url = "https://example.com", isSuspicious = false),
        )
        val vm = buildVm(requestData)
        vm.start()

        vm.onEvent(AutofillUiEvent.OnItemSelected(login.id))

        assertIs<AssociationDialogVisibility.Visible>(vm.uiState.value.associationDialogVisibility)
        assertEquals(
            "bob",
            (vm.uiState.value.associationDialogVisibility as AssociationDialogVisibility.Visible).itemName,
        )
    }

    @Test
    fun `associating adds domain and sends Fill event`() = runTest {
        resolver.resolutions = mapOf("https://example.com" to "example.com")
        val login = testLogin(username = "bob", name = "bob", domainInfos = emptySet())
        loginRepo.seed(login)

        val fields = listOf(credField(FieldType.Credentials.Username, viewId = 1))
        val requestData = FillRequestData.App(
            form(fields = fields, url = "https://example.com", isSuspicious = false),
        )
        val vm = buildVm(requestData)
        vm.start()

        vm.onEvent(AutofillUiEvent.OnItemSelected(login.id))
        assertIs<AssociationDialogVisibility.Visible>(vm.uiState.value.associationDialogVisibility)

        val eventDeferred = async { vm.events.first() }
        vm.onEvent(AutofillUiEvent.OnAssociate)
        val event = eventDeferred.await()

        assertIs<AutofillEvent.Fill>(event)
        assertTrue(loginRepo.getLoginById(login.id)!!.domainInfos.isNotEmpty())
    }

    @Test
    fun `authenticating with suggestion sends Fill event`() = runTest {
        resolver.resolutions = mapOf("https://example.com" to "example.com")
        val login = testLogin(
            username = "carol",
            domainInfos = setOf(matchingDomainInfo()),
        )
        loginRepo.seed(login)

        val fields = listOf(credField(FieldType.Credentials.Username, viewId = 1))
        val theForm = form(fields = fields, url = "https://example.com", isSuspicious = false)
        val requestData = FillRequestData.Suggestion(theForm, vaultId = login.id, index = 0)
        val vm = buildVm(requestData)

        val biometricDeferred = async { vm.biometricFlow.first() }
        vm.start()

        val eventDeferred = async { vm.events.first() }
        vm.onEvent(AutofillUiEvent.OnAuthenticated)
        val event = eventDeferred.await()

        assertIs<AutofillEvent.Fill>(event)
        biometricDeferred.cancel()
    }

    @Test
    fun `authenticating without suggestion sends Abort`() = runTest {
        val requestData = FillRequestData.App(form(isSuspicious = false))
        val vm = buildVm(requestData)
        vm.start()

        val eventDeferred = async { vm.events.first() }
        vm.onEvent(AutofillUiEvent.OnAuthenticated)

        assertEquals(AutofillEvent.Abort, eventDeferred.await())
    }

    @Test
    fun `totp fill with null totp aborts`() = runTest {
        resolver.resolutions = mapOf("https://example.com" to "example.com")
        val login = testLogin(
            username = "alice",
            domainInfos = setOf(matchingDomainInfo()),
        )
        loginRepo.seed(login)

        val fields = listOf(credField(FieldType.TOTP, viewId = 1))
        val requestData = FillRequestData.App(
            form(
                fields = fields,
                url = "https://example.com",
                isSuspicious = false,
                type = FormType.TOTP
            ),
        )
        val vm = buildVm(requestData)
        vm.start()

        val eventDeferred = async { vm.events.first() }
        vm.onEvent(AutofillUiEvent.OnItemSelected(login.id))

        assertEquals(AutofillEvent.Abort, eventDeferred.await())
    }

    @Test
    fun `totp fill with generator failure aborts`() = runTest {
        resolver.resolutions = mapOf("https://example.com" to "example.com")
        val login = testLogin(
            username = "alice",
            domainInfos = setOf(matchingDomainInfo()),
        )
        loginRepo.seed(login)
        totpRepo.seed(login.id, minimalTotp(login.id))
        totpGenerator.result = Result.Failure(TotpError.CryptoFailed)

        val fields = listOf(credField(FieldType.TOTP, viewId = 1))
        val requestData = FillRequestData.App(
            form(
                fields = fields,
                url = "https://example.com",
                isSuspicious = false,
                type = FormType.TOTP
            ),
        )
        val vm = buildVm(requestData)
        vm.start()

        val eventDeferred = async { vm.events.first() }
        vm.onEvent(AutofillUiEvent.OnItemSelected(login.id))

        assertEquals(AutofillEvent.Abort, eventDeferred.await())
    }

    @Test
    fun `totp fill success sends Fill event`() = runTest {
        resolver.resolutions = mapOf("https://example.com" to "example.com")
        val login = testLogin(
            username = "alice",
            domainInfos = setOf(matchingDomainInfo()),
        )
        loginRepo.seed(login)
        totpRepo.seed(login.id, minimalTotp(login.id))
        totpGenerator.result = Result.Success(TotpValue("123456", 0L, 30000L))

        val fields = listOf(credField(FieldType.TOTP, viewId = 1))
        val requestData = FillRequestData.App(
            form(
                fields = fields,
                url = "https://example.com",
                isSuspicious = false,
                type = FormType.TOTP
            ),
        )
        val vm = buildVm(requestData)
        vm.start()

        val eventDeferred = async { vm.events.first() }
        vm.onEvent(AutofillUiEvent.OnItemSelected(login.id))
        val event = eventDeferred.await()

        assertIs<AutofillEvent.Fill>(event)
        assertEquals("123456", datasetProvider.getFillingDatasetCalls.last().first().value)
    }

    @Test
    fun `sms otp request shows the pending dialog`() = runTest {
        smsCodeRepo.gate = CompletableDeferred()

        val vm = buildVm(smsOtpRequest(listOf(credField(FieldType.TOTP, viewId = 1))))
        vm.start()

        assertTrue(vm.uiState.value.showSmsPending)
    }

    @Test
    fun `sms code received sends Fill event with the code`() = runTest {
        smsCodeRepo.enqueue(Result.Success("123456"))

        val vm = buildVm(smsOtpRequest(listOf(credField(FieldType.TOTP, viewId = 1))))
        val eventDeferred = async { vm.events.first() }
        vm.start()
        val event = eventDeferred.await()

        assertIs<AutofillEvent.Fill>(event)
        assertEquals("123456", datasetProvider.getFillingDatasetCalls.last().first().value)
        assertFalse(vm.uiState.value.showSmsPending)
    }

    @Test
    fun `sms retrieval failures abort`() = runTest {
        val failures = listOf(
            SmsCodeFailure.Timeout,
            SmsCodeFailure.Unavailable,
            SmsCodeFailure.Unknown(IllegalStateException("boom")),
        )

        failures.forEach { failure ->
            smsCodeRepo = FakeSmsCodeRepository()
            smsCodeRepo.enqueue(Result.Failure(failure))

            val vm = buildVm(smsOtpRequest(listOf(credField(FieldType.TOTP, viewId = 1))))
            val eventDeferred = async { vm.events.first() }
            vm.start()

            assertEquals(AutofillEvent.Abort, eventDeferred.await(), "failed for $failure")
            assertFalse(vm.uiState.value.showSmsPending, "failed for $failure")
        }
    }

    @Test
    fun `sms otp request with no fields aborts`() = runTest {
        val vm = buildVm(smsOtpRequest(emptyList()))
        val eventDeferred = async { vm.events.first() }
        vm.start()

        assertEquals(AutofillEvent.Abort, eventDeferred.await())
        assertFalse(vm.uiState.value.showSmsPending)
    }

    @Test
    fun `consent required emits RequestSmsConsent and keeps the dialog up`() = runTest {
        smsCodeRepo.enqueue(Result.Failure(SmsCodeFailure.ConsentRequired(testIntentSender())))

        val vm = buildVm(smsOtpRequest(listOf(credField(FieldType.TOTP, viewId = 1))))
        val eventDeferred = async { vm.events.first() }
        vm.start()

        assertIs<AutofillEvent.RequestSmsConsent>(eventDeferred.await())
        assertTrue(vm.uiState.value.showSmsPending)
    }

    @Test
    fun `granted consent retries the retrieval and fills`() = runTest {
        smsCodeRepo.enqueue(
            Result.Failure(SmsCodeFailure.ConsentRequired(testIntentSender())),
            Result.Success("654321"),
        )

        val vm = buildVm(smsOtpRequest(listOf(credField(FieldType.TOTP, viewId = 1))))
        val consentDeferred = async { vm.events.first() }
        vm.start()
        assertIs<AutofillEvent.RequestSmsConsent>(consentDeferred.await())

        val fillDeferred = async { vm.events.first() }
        vm.onEvent(AutofillUiEvent.OnSmsConsentResult(granted = true))

        assertIs<AutofillEvent.Fill>(fillDeferred.await())
        assertEquals("654321", datasetProvider.getFillingDatasetCalls.last().first().value)
    }

    @Test
    fun `denied consent aborts`() = runTest {
        smsCodeRepo.enqueue(Result.Failure(SmsCodeFailure.ConsentRequired(testIntentSender())))

        val vm = buildVm(smsOtpRequest(listOf(credField(FieldType.TOTP, viewId = 1))))
        val consentDeferred = async { vm.events.first() }
        vm.start()
        consentDeferred.await()

        val abortDeferred = async { vm.events.first() }
        vm.onEvent(AutofillUiEvent.OnSmsConsentResult(granted = false))

        assertEquals(AutofillEvent.Abort, abortDeferred.await())
        assertFalse(vm.uiState.value.showSmsPending)
    }

    @Test
    fun `consent required twice aborts instead of looping`() = runTest {
        smsCodeRepo.enqueue(
            Result.Failure(SmsCodeFailure.ConsentRequired(testIntentSender())),
            Result.Failure(SmsCodeFailure.ConsentRequired(testIntentSender())),
        )

        val vm = buildVm(smsOtpRequest(listOf(credField(FieldType.TOTP, viewId = 1))))
        val consentDeferred = async { vm.events.first() }
        vm.start()
        consentDeferred.await()

        val abortDeferred = async { vm.events.first() }
        vm.onEvent(AutofillUiEvent.OnSmsConsentResult(granted = true))

        assertEquals(AutofillEvent.Abort, abortDeferred.await())
        assertEquals(2, smsCodeRepo.callCount)
    }

    @Test
    fun `cancelling sms retrieval aborts and clears the pending state`() = runTest {
        smsCodeRepo.gate = CompletableDeferred()

        val vm = buildVm(smsOtpRequest(listOf(credField(FieldType.TOTP, viewId = 1))))
        vm.start()
        assertTrue(vm.uiState.value.showSmsPending)

        val abortDeferred = async { vm.events.first() }
        vm.onEvent(AutofillUiEvent.OnCancelSmsCode)

        assertEquals(AutofillEvent.Abort, abortDeferred.await())
        assertFalse(vm.uiState.value.showSmsPending)
    }

    @Test
    fun `a code arriving after cancellation does not fill`() = runTest {
        val gate = CompletableDeferred<Unit>()
        smsCodeRepo.gate = gate
        smsCodeRepo.enqueue(Result.Success("999999"))

        val vm = buildVm(smsOtpRequest(listOf(credField(FieldType.TOTP, viewId = 1))))
        vm.start()

        val abortDeferred = async { vm.events.first() }
        vm.onEvent(AutofillUiEvent.OnCancelSmsCode)
        assertEquals(AutofillEvent.Abort, abortDeferred.await())

        gate.complete(Unit)

        assertTrue(datasetProvider.getFillingDatasetCalls.isEmpty())
    }
}
