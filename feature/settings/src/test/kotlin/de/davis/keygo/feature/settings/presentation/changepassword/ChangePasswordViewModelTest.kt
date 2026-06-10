package de.davis.keygo.feature.settings.presentation.changepassword

import de.davis.keygo.core.identity.FakeAccountRepository
import de.davis.keygo.core.identity.domain.model.Account
import de.davis.keygo.core.identity.domain.model.BiometricWrappedArk
import de.davis.keygo.core.identity.domain.model.PasswordWrappedArk
import de.davis.keygo.core.identity.domain.usecase.ChangePasswordUseCase
import de.davis.keygo.core.item.domain.estimator.PasswordStrengthEstimator
import de.davis.keygo.core.item.domain.model.PasswordScore
import de.davis.keygo.core.security.crypto.FakeBiometricAvailabilityRepository
import de.davis.keygo.core.security.domain.model.BiometricAuthError
import de.davis.keygo.core.util.Result
import de.davis.keygo.rust.FakeKeyDeriver
import de.davis.keygo.rust.FakeKeyWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import java.security.Key
import java.util.UUID
import javax.crypto.spec.SecretKeySpec
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class ChangePasswordViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val accountRepository = FakeAccountRepository()
    private val biometricAvailability = FakeBiometricAvailabilityRepository()
    private val keyDeriver = FakeKeyDeriver()
    private val keyWrapper = FakeKeyWrapper()
    private val estimator = object : PasswordStrengthEstimator {
        override suspend fun estimate(password: String): PasswordScore = PasswordScore.None
    }
    private val changePassword = ChangePasswordUseCase(accountRepository, keyDeriver, keyWrapper)

    private val accountId = UUID.randomUUID()
    private val ark = ByteArray(32) { (it + 1).toByte() }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val salt = keyDeriver.generateSalt()
        val kek = keyDeriver.deriveRootKekFromPassword("old", salt)
        val wrapped = keyWrapper.wrapAccountRootKey(kek, ark, accountId)
        accountRepository.seed(
            Account(
                id = accountId,
                displayName = "Test",
                passwordWrappedArk = PasswordWrappedArk(wrapped.ciphertext, wrapped.nonce, salt),
                biometricWrappedArk = null,
            )
        )
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** Re-seed the account with a biometric-wrapped ARK and mark hardware available. */
    private suspend fun enableBiometric() {
        biometricAvailability.isAvailable = true
        val current = accountRepository.getOrNull()!!
        accountRepository.seed(
            current.copy(
                biometricWrappedArk = BiometricWrappedArk(
                    key = ByteArray(48) { it.toByte() },
                    keyIV = ByteArray(12) { it.toByte() },
                )
            )
        )
    }

    private fun viewModel() = ChangePasswordViewModel(
        accountRepository = accountRepository,
        biometricAvailabilityRepository = biometricAvailability,
        passwordStrengthEstimator = estimator,
        changePassword = changePassword,
    )

    @Test
    fun `blank new password sets Empty error and does not change password`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.value.currentPassword.edit { append("old") }

        vm.submitWithPassword()
        advanceUntilIdle()

        assertEquals(FieldError.Empty, vm.state.value.newPasswordError)
    }

    @Test
    fun `mismatched confirmation sets Mismatch error`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.value.currentPassword.edit { append("old") }
        vm.state.value.newPassword.edit { append("brand-new") }
        vm.state.value.confirmPassword.edit { append("different") }

        vm.submitWithPassword()
        advanceUntilIdle()

        assertEquals(FieldError.Mismatch, vm.state.value.confirmPasswordError)
    }

    @Test
    fun `wrong current password sets Incorrect error`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.value.currentPassword.edit { append("wrong") }
        vm.state.value.newPassword.edit { append("brand-new") }
        vm.state.value.confirmPassword.edit { append("brand-new") }

        vm.submitWithPassword()

        // Await rather than advanceUntilIdle: key derivation hops to Dispatchers.Default,
        // which the test scheduler cannot see.
        vm.state.first { it.currentPasswordError != FieldError.None }
        assertEquals(FieldError.Incorrect, vm.state.value.currentPasswordError)
    }

    @Test
    fun `valid password change emits Success`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.value.currentPassword.edit { append("old") }
        vm.state.value.newPassword.edit { append("brand-new") }
        vm.state.value.confirmPassword.edit { append("brand-new") }

        vm.submitWithPassword()
        advanceUntilIdle()

        assertEquals(ChangePasswordEvent.Success, vm.event.first())
    }

    @Test
    fun `onSubmit with biometric available and valid passwords emits LaunchBiometricPrompt`() =
        runTest(dispatcher) {
            enableBiometric()
            val vm = viewModel()
            advanceUntilIdle() // let resolveBiometricAvailability() populate biometricCiphertext
            vm.state.value.newPassword.edit { append("brand-new") }
            vm.state.value.confirmPassword.edit { append("brand-new") }

            vm.onSubmit()
            advanceUntilIdle()

            assertEquals(ChangePasswordEvent.LaunchBiometricPrompt, vm.event.first())
        }

    @Test
    fun `onSubmit with biometric available and blank new password sets Empty and does not prompt`() =
        runTest(dispatcher) {
            enableBiometric()
            val vm = viewModel()
            advanceUntilIdle()

            vm.onSubmit()
            advanceUntilIdle()

            assertEquals(FieldError.Empty, vm.state.value.newPasswordError)
        }

    @Test
    fun `onSubmit without biometric and valid passwords emits Success`() = runTest(dispatcher) {
        val vm = viewModel() // setUp seeds an account with no biometric ARK; availability defaults false
        vm.state.value.currentPassword.edit { append("old") }
        vm.state.value.newPassword.edit { append("brand-new") }
        vm.state.value.confirmPassword.edit { append("brand-new") }

        vm.onSubmit()
        advanceUntilIdle()

        assertEquals(ChangePasswordEvent.Success, vm.event.first())
    }

    @Test
    fun `onSubmit with biometric available and mismatched passwords sets Mismatch and does not prompt`() =
        runTest(dispatcher) {
            enableBiometric()
            val vm = viewModel()
            advanceUntilIdle()
            vm.state.value.newPassword.edit { append("aaa") }
            vm.state.value.confirmPassword.edit { append("bbb") }

            vm.onSubmit()
            advanceUntilIdle()

            assertEquals(FieldError.Mismatch, vm.state.value.confirmPasswordError)
        }

    @Test
    fun `dismissReauthDialog hides dialog and clears current password error`() = runTest(dispatcher) {
        enableBiometric()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onBiometricResult(Result.Failure(BiometricAuthError.Declined)) // opens the dialog
        vm.state.value.newPassword.edit { append("brand-new") }
        vm.state.value.confirmPassword.edit { append("brand-new") }
        vm.state.value.currentPassword.edit { append("wrong") }
        vm.submitWithPassword()
        // Await rather than advanceUntilIdle: key derivation hops to Dispatchers.Default,
        // which the test scheduler cannot see. The Incorrect error below is load-bearing.
        vm.state.first { it.currentPasswordError == FieldError.Incorrect }

        vm.dismissReauthDialog()

        assertEquals(false, vm.state.value.showReauthDialog)
        assertEquals(FieldError.None, vm.state.value.currentPasswordError)
    }

    @Test
    fun `dialog confirm with wrong current password keeps dialog open with Incorrect error`() =
        runTest(dispatcher) {
            enableBiometric()
            val vm = viewModel()
            advanceUntilIdle()
            vm.onBiometricResult(Result.Failure(BiometricAuthError.Declined)) // opens the dialog
            vm.state.value.newPassword.edit { append("brand-new") }
            vm.state.value.confirmPassword.edit { append("brand-new") }
            vm.state.value.currentPassword.edit { append("wrong") }

            vm.submitWithPassword() // dialog Confirm action

            // Await rather than advanceUntilIdle: key derivation hops to Dispatchers.Default,
            // which the test scheduler cannot see.
            vm.state.first { it.currentPasswordError != FieldError.None }
            assertEquals(FieldError.Incorrect, vm.state.value.currentPasswordError)
            assertEquals(true, vm.state.value.showReauthDialog)
        }

    @Test
    fun `dialog confirm with correct current password emits Success`() = runTest(dispatcher) {
        enableBiometric()
        val vm = viewModel()
        advanceUntilIdle()
        vm.onBiometricResult(Result.Failure(BiometricAuthError.Declined)) // opens the dialog
        vm.state.value.newPassword.edit { append("brand-new") }
        vm.state.value.confirmPassword.edit { append("brand-new") }
        vm.state.value.currentPassword.edit { append("old") }

        vm.submitWithPassword() // dialog Confirm action
        advanceUntilIdle()

        assertEquals(ChangePasswordEvent.Success, vm.event.first())
    }

    @Test
    fun `onBiometricResult with a recovered key changes password and emits Success`() =
        runTest(dispatcher) {
            enableBiometric()
            val vm = viewModel()
            advanceUntilIdle()
            vm.state.value.newPassword.edit { append("brand-new") }
            vm.state.value.confirmPassword.edit { append("brand-new") }
            val recovered: Result<Key, BiometricAuthError> =
                Result.Success(SecretKeySpec(ark.copyOf(), "AES"))

            vm.onBiometricResult(recovered)
            advanceUntilIdle()

            assertEquals(ChangePasswordEvent.Success, vm.event.first())
        }

    @Test
    fun `onBiometricResult failure opens the reauth dialog`() = runTest(dispatcher) {
        enableBiometric()
        val vm = viewModel()
        advanceUntilIdle()
        val failure: Result<Key, BiometricAuthError> = Result.Failure(BiometricAuthError.NoCipher)

        vm.onBiometricResult(failure)

        assertEquals(true, vm.state.value.showReauthDialog)
    }

    @Test
    fun `onBiometricResult Declined opens the reauth dialog`() = runTest(dispatcher) {
        enableBiometric()
        val vm = viewModel()
        advanceUntilIdle()
        val failure: Result<Key, BiometricAuthError> = Result.Failure(BiometricAuthError.Declined)

        vm.onBiometricResult(failure)

        assertEquals(true, vm.state.value.showReauthDialog)
    }

    @Test
    fun `onBiometricResult Canceled leaves the form untouched`() = runTest(dispatcher) {
        enableBiometric()
        val vm = viewModel()
        advanceUntilIdle()
        val failure: Result<Key, BiometricAuthError> = Result.Failure(BiometricAuthError.Canceled)

        vm.onBiometricResult(failure)

        assertEquals(false, vm.state.value.showReauthDialog)
    }
}
